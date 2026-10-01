package se.elitrobban.elbilsladdning.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.elitrobban.elbilsladdning.scraper.BlocketUsedPriceClient;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vad en elbil kostar i Sverige idag — ny hos handlare och begagnad — ur Blockets annonser.
 *
 * <p><b>Varför Blocket och inte {@code ev_spec.price_kr}.</b> Priset i {@code ev_spec} är ett
 * europeiskt listpris i euro från ev-database.org, omräknat med en fast kurs och aldrig
 * uppdaterat efter första skrapningen. Märken som prissätter olika per land hamnar långt fel.
 * Blocket har däremot kategorin "Ny bil till salu" ({@code sales_form=2}) där handlarna lägger
 * ut nya bilar med svenskt pris: mätt 2026-10-01 låg Volvo EX30 årsmodell 2027 på
 * 432 000–547 900 kr. Samma källa ger begagnatpriset, så ny och begagnad jämförs mot samma
 * marknad i samma ögonblick.
 *
 * <p><b>Modellen avgörs av annonsens {@code make}/{@code model}, inte av sökordet.</b>
 * Blockets fritext är lös: "Volvo EX30" gav 2026-10-01 även EX40 och EC40. En annons räknas bara
 * när dess märke + modell är början av bilens namn i databasen — se {@link #sammaModell}.
 *
 * <p>Priserna gäller modellen i alla versioner ("Volvo EX30"), inte en enskild trimnivå:
 * Blockets {@code model_specification} är fritext som handlarna skriver som de vill.
 *
 * @author Robert Andersson Kopler
 */
@Service
public class ElbilsmarknadService {

    private static final Logger log = LoggerFactory.getLogger(ElbilsmarknadService.class);

    /** Annonspriser rör sig över veckor, inte timmar — ett dygn räcker gott. */
    private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L;
    /** Tak så att cachen inte växer obegränsat; bilistan har runt 520 namn. */
    private static final int CACHE_MAX = 1_000;

    /** Under detta är en "ny bil" en leasingannons med månadsavgiften i prisfältet. */
    static final int NY_LAGSTA_PRIS_KR = 100_000;
    /** En ny bil eller demobil har knappt rullat. */
    static final int NY_MAX_MIL = 200;
    /** Samma milgräns som värdetappslistan — se {@link BlocketUsedPriceClient}. */
    static final int BEGAGNAD_MAX_MIL = 15_000;
    static final int BEGAGNAD_LAGSTA_PRIS_KR = 40_000;

    public static final String KALLA = "Blocket — annonser för nya och begagnade elbilar";

    /** Priser för ett urval annonser. {@code antal} är 0 när inget dugligt hittades. */
    public record Prisdel(int antal, Integer medianKr, Integer billigastKr,
                          Integer arsmodellFran, Integer arsmodellTill) {
        static Prisdel tom() { return new Prisdel(0, null, null, null, null); }
    }

    /** Ny och begagnad för en modell. {@code modell} är Blockets märke + modell, t.ex. "Volvo EX30". */
    public record Marknadsbild(String bil, String modell, Prisdel ny, Prisdel begagnad, long hamtadMs) {}

    /** Annonslistorna per sökord. Varianterna av samma modell delar sökord och därmed anrop. */
    private record Annonser(List<JsonNode> nya, List<JsonNode> begagnade, long tid) {}

    private final BlocketUsedPriceClient blocket;
    private final Map<String, Annonser> cache = new ConcurrentHashMap<>();

    public ElbilsmarknadService(BlocketUsedPriceClient blocket) {
        this.blocket = blocket;
    }

    /**
     * Marknadsbilden för en bil ur databasen. Hämtar från Blocket om den inte finns i cachen.
     *
     * @return tom när Blocket inte svarade — ett fel får aldrig bli ett pris, och det cachas inte
     */
    public Optional<Marknadsbild> forBil(String bilnamn) {
        Optional<Marknadsbild> cachad = cachad(bilnamn);
        if (cachad.isPresent()) return cachad;

        String sokord = sokord(bilnamn);
        List<JsonNode> nya = blocket.elbilsannonser(sokord, BlocketUsedPriceClient.SALES_FORM_NY);
        List<JsonNode> beg = blocket.elbilsannonser(sokord, BlocketUsedPriceClient.SALES_FORM_BEGAGNAD);
        if (nya == null || beg == null) return Optional.empty();

        Annonser a = new Annonser(nya, beg, System.currentTimeMillis());
        if (cache.size() >= CACHE_MAX) cache.clear();
        cache.put(sokord, a);
        Marknadsbild bild = berakna(bilnamn, a.nya(), a.begagnade(), a.tid());
        log.info("marknad: {} (sök \"{}\") — ny {} st, median {} kr; begagnad {} st, median {} kr",
                bilnamn, sokord, bild.ny().antal(), bild.ny().medianKr(),
                bild.begagnad().antal(), bild.begagnad().medianKr());
        return Optional.of(bild);
    }

    /** Bara det som redan finns i cachen — för AI-prompten, som inte ska vänta på Blocket i onödan. */
    public Optional<Marknadsbild> cachad(String bilnamn) {
        if (bilnamn == null) return Optional.empty();
        Annonser a = cache.get(sokord(bilnamn));
        if (a == null || System.currentTimeMillis() - a.tid() > CACHE_TTL_MS) return Optional.empty();
        return Optional.of(berakna(bilnamn, a.nya(), a.begagnade(), a.tid()));
    }

    /** Räknar fram marknadsbilden ur två annonslistor. Paketsynlig så den går att pröva utan HTTP. */
    static Marknadsbild berakna(String bilnamn, List<JsonNode> nya, List<JsonNode> begagnade, long hamtadMs) {
        String modell = null;
        List<Integer> nyPriser = new ArrayList<>(), nyAr = new ArrayList<>();
        for (JsonNode a : nya) {
            if (!sammaModell(bilnamn, a)) continue;
            Integer pris = pris(a, NY_LAGSTA_PRIS_KR);
            if (pris == null || mil(a) > NY_MAX_MIL) continue;
            nyPriser.add(pris);
            if (a.path("year").isNumber()) nyAr.add(a.path("year").asInt());
            if (modell == null) modell = visningsnamn(a);
        }
        List<Integer> begPriser = new ArrayList<>(), begAr = new ArrayList<>();
        for (JsonNode a : begagnade) {
            if (!sammaModell(bilnamn, a)) continue;
            Integer pris = pris(a, BEGAGNAD_LAGSTA_PRIS_KR);
            // Saknad mätarställning på en begagnad bil går inte att pröva mot milgränsen.
            if (pris == null || !a.path("mileage").isNumber() || mil(a) > BEGAGNAD_MAX_MIL) continue;
            begPriser.add(pris);
            if (a.path("year").isNumber()) begAr.add(a.path("year").asInt());
            if (modell == null) modell = visningsnamn(a);
        }
        return new Marknadsbild(bilnamn, modell, prisdel(nyPriser, nyAr), prisdel(begPriser, begAr), hamtadMs);
    }

    static Prisdel prisdel(List<Integer> priser, List<Integer> ar) {
        if (priser.isEmpty()) return Prisdel.tom();
        List<Integer> p = new ArrayList<>(priser);
        Collections.sort(p);
        Integer fran = ar.isEmpty() ? null : Collections.min(ar);
        Integer till = ar.isEmpty() ? null : Collections.max(ar);
        return new Prisdel(p.size(), p.get(p.size() / 2), p.get(0), fran, till);
    }

    /**
     * Sant när annonsens märke + modell är början av bilens namn, ord för ord.
     *
     * <p>"Volvo EX30" matchar "Volvo EX30 Twin Motor" men inte "Volvo EX40 Single Motor", och
     * "Tesla Model 3" inte "Tesla Model Y". Stavningen skiljer sig mellan databasen och Blocket
     * (mätt 2026-10-01), och {@link #norm} jämnar ut det:
     * <ul>
     *   <li>"Mercedes EQA 250" mot Blockets "Mercedes-Benz" + "EQA250"</li>
     *   <li>"Volkswagen ID.Buzz" mot "ID. Buzz", och "Škoda" mot "Skoda"</li>
     *   <li>"MG4 Long Range" mot "MG" + "MG4" — där modellen redan bär märket tas den ensam</li>
     * </ul>
     *
     * <p><b>En ensam siffra direkt efter annonsens modell stoppar matchningen.</b> Blocket har
     * kvar den gamla "Hyundai IONIQ" (2016–2022) som egen modell, och den är början av "Hyundai
     * IONIQ 5" ord för ord — utan spärren drogs 2018 års Ioniq in bland Ioniq 5:orna. Flersiffriga
     * tal släpps igenom: i "Nissan Leaf (50 kWh)" och "Škoda Elroq 85" är de versionen.
     */
    static boolean sammaModell(String bilnamn, JsonNode annons) {
        if (!"el".equalsIgnoreCase(annons.path("fuel").asText("").trim())) return false;
        String make = norm(annons.path("make").asText(""));
        String model = norm(annons.path("model").asText(""));
        if (model.isEmpty()) return false;
        String annonsNamn = model.equals(make) || model.startsWith(make + " ") ? model : make + " " + model;

        String bil = norm(bilnamn);
        // Annonsen mer detaljerad än databasen: "Porsche Taycan" mot Blockets "Taycan 4S".
        if ((annonsNamn + " ").startsWith(bil + " ")) return true;
        if (!(bil + " ").startsWith(annonsNamn + " ")) return false;
        String resten = (bil + " ").substring(annonsNamn.length() + 1);
        return !resten.matches("\\d .*");
    }

    /**
     * Fritexten till Blocket: märket och modellbeteckningen, utan versionen.
     *
     * <p>Namnen i databasen bär effekt och batteri ("Audi A2 e-tron 125 kW - 50 kWh", "Nissan
     * Leaf 62 kWh", "Škoda Elroq 85") som inte står i annonserna. Regeln:
     * <ul>
     *   <li>Ordet efter märket tas alltid, och är det en beteckning med siffra (EX30, ID.4, MG4,
     *       500e) är modellen klar där.</li>
     *   <li>Ett tredje ord tas bara om det är ett ensamt tecken: "Tesla Model 3", "Tesla Model Y",
     *       "Hyundai Ioniq 5". Allt annat är versionen, och fritexten blir sämre av den — mätt
     *       2026-10-01 gav "Škoda Enyaq iV" 4 nya Enyaq medan "Skoda Enyaq" gav 29.</li>
     * </ul>
     *
     * <p>Diakriterna tas bort: Blocket skriver märket "Skoda".
     */
    static String sokord(String bilnamn) {
        String ascii = bilnamn == null ? "" : Normalizer.normalize(bilnamn, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String[] ord = ascii.isBlank() ? new String[0] : ascii.trim().split("\\s+");
        if (ord.length == 0) return "";
        // "MG4 Long Range": namnet börjar med modellen och märket står inte för sig.
        if (harSiffra(ord[0])) return ord[0];
        StringBuilder sb = new StringBuilder(ord[0]);
        if (ord.length < 2) return sb.toString();
        sb.append(' ').append(ord[1]);
        if (harSiffra(ord[1]) || ord.length < 3) return sb.toString();
        if (ord[2].matches("[\\p{L}\\d]")) sb.append(' ').append(ord[2]);
        return sb.toString();
    }

    private static boolean harSiffra(String s) {
        return s.chars().anyMatch(Character::isDigit);
    }

    private static Integer pris(JsonNode annons, int lagsta) {
        JsonNode p = annons.path("price").path("amount");
        return p.isNumber() && p.asInt() >= lagsta ? p.asInt() : null;
    }

    private static int mil(JsonNode annons) {
        return annons.path("mileage").asInt(0);
    }

    private static String visningsnamn(JsonNode annons) {
        return (annons.path("make").asText("") + " " + annons.path("model").asText("")).trim();
    }

    /**
     * Gemener utan diakriter, med bokstäver och siffror som egna ord: "EQA250" och "EQA 250"
     * blir båda "eqa 250", "ID.Buzz" och "ID. Buzz" båda "id buzz".
     */
    static String norm(String s) {
        String utanAccent = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return utanAccent.toLowerCase()
                .replace("mercedes-benz", "mercedes")
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("(?<=[a-z])(?=\\d)|(?<=\\d)(?=[a-z])", " ")
                .trim().replaceAll(" +", " ");
    }
}
