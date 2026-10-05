package se.elitrobban.elbilsladdning.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Approximate DC prices per major Swedish charging network,
 * sourced from each operator's public pricing page (utan abonnemang/roaming).
 * Updated 2026-06-13. Always shown with a disclaimer to check the operator's app.
 *
 * <p><b>Uppdateras automatiskt sedan 2026-09-27.</b> Tabellen nedan är numera en RESERV. CarAdvice
 * nattrutin kontrollerar nätverkens egna prissidor och publicerar det den kunnat belägga på
 * {@code GET /api/laddpriser}; den här tjänsten hämtar det var sjätte timme och lägger det över
 * reserven. Förut stod priserna från juni kvar tills någon råkade ändra dem för hand.
 * Tre vakter: bara ett pris i kronor per kWh (eller "Gratis ..."), bara 1-15 kr/kWh, och aldrig
 * ett hopp på mer än {@value #MAX_ANDRING_PROCENT} % mot reserven - en felläsning ska inte kunna
 * göra IONITY gratis eller Lidl dyrast i landet. Svarar CarAdvice inte står förra listan kvar.
 *
 * @author Robert Andersson Kopler
 */
@Service
public class OperatorPriceService {

    private static final Logger log = LoggerFactory.getLogger(OperatorPriceService.class);
    static final int MAX_ANDRING_PROCENT = 60;

    @Value("${caradvice.api.url:https://caradvice.onrender.com}")
    private String caradviceUrl = "https://caradvice.onrender.com";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper json = new ObjectMapper();

    /** Priserna som gäller just nu: reserven, med CarAdvice uppdateringar ovanpå. */
    private volatile LinkedHashMap<String, String> aktuella = new LinkedHashMap<>(PRICES);

    // Order matters — first matching entry wins (more specific names first)
    private static final LinkedHashMap<String, String> PRICES = new LinkedHashMap<>();

    static {
        PRICES.put("ionity",         "~6,96 kr/kWh");
        PRICES.put("allego",         "~6,50 kr/kWh");
        PRICES.put("eviny",          "~3,99 kr/kWh");
        PRICES.put("nima",           "~5,99 kr/kWh");
        PRICES.put("northe",         "~5,20 kr/kWh");
        PRICES.put("hedin",          "~5,20 kr/kWh");
        PRICES.put("kungsbacka volvo","~6,35 kr/kWh");
        PRICES.put("tesla",          "~4,50 kr/kWh");
        PRICES.put("vattenfall",  "~3,49 kr/kWh");
        PRICES.put("incharge",    "~3,49 kr/kWh");
        PRICES.put("recharge",    "~3,49 kr/kWh");
        PRICES.put("circle k",    "~5,99 kr/kWh");
        PRICES.put("circlek",     "~5,99 kr/kWh");
        PRICES.put("bee",         "~3,29 kr/kWh");
        PRICES.put("mer",         "~6,24 kr/kWh");
        // Betalplattform utan eget publikt kWh-pris — baserat på Mer-DC 6 kr/kWh
        // via Easypark + 15 % serviceavgift (Small-planen). Tillagd 2026-07-15.
        PRICES.put("easypark",    "~6,90 kr/kWh");
        PRICES.put("e.on",        "~4,75 kr/kWh");
        PRICES.put("eon",         "~4,75 kr/kWh");
        PRICES.put("clever",      "~3,99 kr/kWh");
        PRICES.put("chargenode",            "~5,00 kr/kWh");
        PRICES.put("p-hus kungsgatan 6",    "~5,00 kr/kWh");
        PRICES.put("kungsmässan",           "~5,00 kr/kWh");
        PRICES.put("bissmarksgatan",        "~4,75 kr/kWh");
        PRICES.put("borgmästaregatan",      "~4,75 kr/kWh");
        PRICES.put("lidl",                "~5,80 kr/kWh"); // lidl.se 2026-10-05, DC utan app (var 2,99 sedan juni - för långt bort för 60 %-vakten)
        PRICES.put("ikea",                "Gratis (för kunder)");
        PRICES.put("preem",               "~3,49 kr/kWh");
        PRICES.put("st1",                 "~3,49 kr/kWh");
    }

    /** Hämtar nattrutinens belagda priser från CarAdvice. Första gången en minut efter uppstart. */
    @Scheduled(initialDelay = 60_000L, fixedRate = 6 * 3_600_000L)
    public void hamtaFranCarAdvice() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(caradviceUrl + "/api/laddpriser"))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> svar = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (svar.statusCode() != 200) throw new IllegalStateException("HTTP " + svar.statusCode());
            int n = tillampa(json.readTree(svar.body()).path("priser"));
            if (n > 0) log.info("Laddpriser: {} nätverk uppdaterade från CarAdvice", n);
        } catch (Exception e) {
            log.warn("Kunde inte hämta laddpriser från CarAdvice ({}), förra listan står kvar", e.getMessage());
        }
    }

    /**
     * Lägger CarAdvice priser över reserven. Ett pris som inte klarar vakterna hoppas över och
     * loggas; resten gäller. Nycklar som inte finns i reserven läggs SIST, så de mer specifika
     * reservnycklarna fortsätter att matcha först.
     *
     * @return antal priser som tillämpades
     */
    int tillampa(JsonNode priser) {
        LinkedHashMap<String, String> ny = new LinkedHashMap<>(PRICES);
        int n = 0;
        for (JsonNode p : priser) {
            String nyckel = p.path("natverk").asString("").trim().toLowerCase();
            String pris = p.path("pris").asString("").trim();
            String fel = fel(nyckel, pris);
            if (fel != null) {
                log.warn("Laddpris för {} avvisat ({}): {}", nyckel, fel, pris);
                continue;
            }
            ny.put(nyckel, pris);
            n++;
        }
        aktuella = ny;
        return n;
    }

    /** @return skälet att avvisa priset, eller null */
    String fel(String nyckel, String pris) {
        if (nyckel.length() < 3) return "för kort nätverksnamn";
        if (pris.toLowerCase().startsWith("gratis")) return null;
        if (!pris.matches("~?\\d{1,2}([,.]\\d{1,2})? kr/kWh")) return "inte ett pris i kr/kWh";
        Double kr = parseKr(pris);
        if (kr == null || kr < 1.0 || kr > 15.0) return "utanför 1-15 kr/kWh";
        Double reserv = parseKr(PRICES.get(nyckel));
        if (reserv != null && Math.abs(kr - reserv) / reserv * 100 > MAX_ANDRING_PROCENT)
            return "hopp på mer än " + MAX_ANDRING_PROCENT + " % mot " + reserv;
        return null;
    }

    /**
     * Returns an approximate price by matching operator name, then station name as fallback.
     * Returns null if neither matches a known network.
     */
    public String getApproxPrice(String operator, String stationName) {
        String price = matchIn(operator);
        if (price == null) price = matchIn(stationName);
        return price;
    }

    private String matchIn(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase();
        // Skip generic OCM placeholder
        if (lower.contains("unknown operator")) return null;
        for (Map.Entry<String, String> e : aktuella.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    /**
     * Parses a price label like "~6,96 kr/kWh" to 6.96.
     * Returns null for non-numeric entries such as "Gratis (för kunder)".
     */
    public Double parseKr(String priceLabel) {
        if (priceLabel == null || priceLabel.isBlank()) return null;
        var m = java.util.regex.Pattern.compile("(\\d+[.,]?\\d*)").matcher(priceLabel);
        if (!m.find()) return null;
        return Double.parseDouble(m.group(1).replace(",", "."));
    }

    /**
     * Average kr/kWh across the table, rounded to 2 decimals. Alias keys for the
     * same network ("circle k"/"circlek", "e.on"/"eon") count once; free/non-numeric
     * entries are excluded.
     */
    /** Ett nätverk och dess riktpris — ytterligheterna i tabellen. */
    public record Ytterlighet(String natverk, double kr) {}

    /**
     * Billigaste respektive dyraste raden i tabellen.
     *
     * <p>Finns för faktakarusellen i webbappen: spridningen mellan nätverken är större än
     * spridningen mellan bilar, och det är den enda siffran vi själva äger och kan hålla
     * aktuell. Gratisrader och andra icke-numeriska värden räknas inte — "gratis" är inte
     * ett lägsta pris, det är en annan sorts uppgift.
     */
    public Ytterlighet billigast() { return ytterlighet(true); }

    public Ytterlighet dyrast() { return ytterlighet(false); }

    private Ytterlighet ytterlighet(boolean lagst) {
        Ytterlighet bast = null;
        for (Map.Entry<String, String> e : aktuella.entrySet()) {
            Double kr = parseKr(e.getValue());
            if (kr == null) continue;
            if (bast == null || (lagst ? kr < bast.kr() : kr > bast.kr())) {
                bast = new Ytterlighet(visningsnamn(e.getKey()), kr);
            }
        }
        return bast;
    }

    // Nycklarna är gemener för matchningen; de här skrivs inte som en versal plus resten.
    private static final Map<String, String> VISNINGSNAMN = Map.of(
            "e.on", "E.ON", "eon", "E.ON", "st1", "St1", "ikea", "IKEA",
            "incharge", "InCharge", "chargenode", "ChargeNode", "circlek", "Circle K");

    static String visningsnamn(String nyckel) {
        String fast = VISNINGSNAMN.get(nyckel);
        if (fast != null) return fast;
        StringBuilder sb = new StringBuilder();
        for (String ord : nyckel.split(" ")) {
            if (ord.isBlank()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(ord.charAt(0))).append(ord.substring(1));
        }
        return sb.toString();
    }

    public double nationalAverageKr() {
        var seen = new java.util.HashSet<String>();
        double sum = 0;
        int n = 0;
        for (Map.Entry<String, String> e : aktuella.entrySet()) {
            if (!seen.add(e.getKey().replaceAll("[^a-z0-9]", ""))) continue;
            Double kr = parseKr(e.getValue());
            if (kr == null) continue;
            sum += kr;
            n++;
        }
        return n == 0 ? 0 : Math.round(sum / n * 100.0) / 100.0;
    }
}
