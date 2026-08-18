package se.elitrobban.elbilsladdning.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Begagnatpriser från Blockets öppna sök-API.
 *
 * <p><b>Medvetet SMAL, och inte en kopia av CarAdvice.</b> Systerprojektet CarAdvice har en
 * {@code BlocketPriceService} som bär mycket mer: leasing kontra köp, prisgolv mot annonser
 * där månadsavgiften står i prisfältet, en milgränstrappa i tre steg och en budgettaksregel.
 * Allt det behövs när man ska avgöra om en bil ryms i en köpares budget. Här ska vi bara veta
 * vad en <i>given årsmodell</i> kostar idag, så den koden är inte portad — en kopia hade blivit
 * två ställen att glömma vid nästa rättning.
 *
 * <p><b>Det som ÄR portat är de tre mätta fällorna</b>, för de gäller lika mycket här:
 * <ul>
 *   <li><b>Milgränsen är inte valfri.</b> Utan den sätts prisbilden av marknadens mest slitna
 *       exemplar. Mätt i CarAdvice 2026-08-08: en Enyaq visades från 229 900 kr — en bil som
 *       gått 21 091 mil — medan billigaste under 10 000 mil kostade 339 900 kr.</li>
 *   <li><b>{@code sales_form} måste sättas</b> till köp (1) och ny (2). Utan det blandas
 *       leasingannonser in, och deras "pris" är en månadsavgift.</li>
 *   <li><b>Svaret kapas vid 50 annonser</b> oavsett {@code lim}, och {@code page} ignoreras.
 *       Fler träffar än så går inte att få — mätt 2026-08-18.</li>
 * </ul>
 *
 * <p>API:t kräver ingen nyckel och kostar ingen kvot.
 */
@Service
public class BlocketUsedPriceClient {

    private static final Logger log = LoggerFactory.getLogger(BlocketUsedPriceClient.class);

    private static final String SEARCH_URL =
            "https://www.blocket.se/mobility/search/api/search/SEARCH_ID_CAR_USED";
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0";

    /** Köp och ny bil till salu — aldrig leasing (5), vars prisfält är en månadsavgift. */
    private static final String SALES_FORM = "&sales_form=1&sales_form=2";

    /**
     * Milgräns i skandinaviska mil.
     *
     * <p>15 000 och inte 10 000: en femårig bil har normalt kört mer än 10 000 mil, och den
     * snävare gränsen tömde underlaget. Mätt 2026-08-18 gav 10 000 mil bara 8 träffar för
     * Audi e-tron medan 15 000 gav rejält fler för samtliga modeller.
     */
    private static final int MAX_MIL = 15_000;

    /** Under detta är beloppet ingen bilaffär — en felskriven annons eller en månadsavgift. */
    private static final int LAGSTA_RIMLIGA_PRIS_KR = 40_000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Prisbilden för en modell och årsmodell. {@code antal} är 0 när inget dugligt hittades. */
    public record Prisbild(int antal, Integer medianKr, Integer billigastKr) {
        public static Prisbild tom() { return new Prisbild(0, null, null); }
    }

    /**
     * Hämtar dagens annonser för modellen och årsmodellen och räknar fram medianen.
     *
     * <p><b>Median, inte medel och inte billigast.</b> Medelvärdet dras iväg av enstaka dyra
     * annonser och billigaste av enstaka vrak; medianen beskriver vad bilen faktiskt kostar.
     * Billigaste returneras ändå som komplement — det är den siffra en fyndjägare vill se —
     * men procenten räknas alltid på medianen.
     *
     * @param sokord    fritext till Blocket, t.ex. "Nissan Leaf"
     * @param arsmodell exakt årsmodell; Blocket filtrerar med year_from/year_to
     * @param trimOrd   valfritt ord som måste finnas i annonsrubriken, t.ex. "e+" — se
     *                  {@link #rubrikMatchar}. Null stänger av filtret.
     */
    public Prisbild prisbildFor(String sokord, int arsmodell, String trimOrd) {
        try {
            String url = SEARCH_URL
                    + "?q=" + URLEncoder.encode(sokord, StandardCharsets.UTF_8)
                    + "&page=0&lim=60&sort=PRICE_ASC" + SALES_FORM
                    + "&year_from=" + arsmodell + "&year_to=" + arsmodell;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(25))
                    .GET().build();

            HttpResponse<String> svar = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (svar.statusCode() != 200) {
                log.warn("Blocket svarade {} för {} {}", svar.statusCode(), sokord, arsmodell);
                return Prisbild.tom();
            }

            JsonNode docs = mapper.readTree(svar.body()).path("docs");
            if (!docs.isArray()) return Prisbild.tom();

            List<Integer> priser = new ArrayList<>();
            for (JsonNode annons : docs) {
                Integer pris = duglittPris(annons, arsmodell, trimOrd);
                if (pris != null) priser.add(pris);
            }
            if (priser.isEmpty()) return Prisbild.tom();

            Collections.sort(priser);
            return new Prisbild(priser.size(), priser.get(priser.size() / 2), priser.get(0));

        } catch (Exception e) {
            // Ett fel får aldrig bli ett PRIS. Tom prisbild betyder "vi vet inte" och raden
            // lämnas orörd av synken — samma skäl som vPIC-vakten i CarAdvice aldrig låter ett
            // hämtningsfel bli ett fynd.
            log.warn("Blocket {} {} misslyckades — {}", sokord, arsmodell, e.getMessage());
            return Prisbild.tom();
        }
    }

    /** Priset om annonsen duger, annars null. Paketsynlig så filtret går att pröva utan HTTP. */
    Integer duglittPris(JsonNode annons, int arsmodell, String trimOrd) {
        JsonNode pris = annons.path("price").path("amount");
        if (!pris.isNumber() || pris.asInt() < LAGSTA_RIMLIGA_PRIS_KR) return null;

        // Årsmodellen filtreras OM trots year_from/year_to. Blocket har visat sig ignorera
        // filterparametrar tyst förr (mileage_to gör det), och ett fel år förgiftar medianen.
        if (annons.path("year").asInt(-1) != arsmodell) return null;

        JsonNode mil = annons.path("mileage");
        if (!mil.isNumber() || mil.asInt() > MAX_MIL) return null;

        if (!arElbil(annons)) return null;
        if (trimOrd != null && !varianttextMatchar(annons, trimOrd)) return null;
        return pris.asInt();
    }

    /**
     * Sant bara för rena elbilar.
     *
     * <p><b>Den här raden räddade hela mätningen.</b> Sökningen "Volvo XC40 Recharge" gav
     * 2026-08-18 fyrtiotre annonser för årsmodell 2021 — och de flesta var
     * <b>laddhybrider</b>: {@code model_specification} löd "Recharge T4 Inscription" och
     * "Recharge T5 R-Design", där T4 och T5 är PHEV-beteckningar. Volvo säljer "Recharge"
     * som namn på både laddhybrid och elbil. Utan det här filtret hade en fyndlista över
     * ELBILAR räknat medianen på laddhybridpriser, och siffran hade sett fullt rimlig ut.
     *
     * <p>Blocket har fältet {@code fuel} med värdet "El" för rena elbilar, vilket är ett
     * betydligt stadigare sätt att skilja dem åt än att gissa på beteckningar i texten.
     */
    static boolean arElbil(JsonNode annons) {
        return "el".equalsIgnoreCase(annons.path("fuel").asText("").trim());
    }

    /**
     * Sant när annonsens varianttext innehåller trimordet.
     *
     * <p><b>Rätt fält är {@code model_specification}</b>, inte rubriken. Mätt 2026-08-18:
     * {@code heading} är bara "BMW i3" eller "Nissan Leaf" — den bara modellen — medan
     * versionen står i {@code model_specification} ("s 120 Ah Comfort Advanced",
     * "62 kWh N-Connecta", "Pro Performance 204hk"). Fältet {@code subject} finns inte alls.
     * Ett filter mot rubriken gav därför NOLL träffar för fem av sex modeller, alltså ett
     * filter som tyst kastade allt.
     *
     * <p><b>Varför filtret behövs.</b> Fritextsökningen matchar alla varianter: "Nissan Leaf"
     * ger både 40 kWh och 62 kWh, medan nypriset vi jämför mot gäller en bestämd version.
     * Utan filtret blandas trimnivåer och procenten blir fel på ett sätt som inte syns.
     */
    static boolean varianttextMatchar(JsonNode annons, String trimOrd) {
        String text = (annons.path("model_specification").asText("") + " "
                + annons.path("heading").asText("") + " "
                + annons.path("facade_title").asText("")).toLowerCase();
        return text.contains(trimOrd.toLowerCase());
    }
}
