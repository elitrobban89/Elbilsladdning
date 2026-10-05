package se.elitrobban.elbilsladdning.service;

import tools.jackson.databind.JsonNode;
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
     * @param saljsNy om bilen säljs ny idag — se {@link #berakna} för varför det behövs
     * @return tom när Blocket inte svarade — ett fel får aldrig bli ett pris, och det cachas inte
     */
    public Optional<Marknadsbild> forBil(String bilnamn, boolean saljsNy) {
        Optional<Marknadsbild> cachad = cachad(bilnamn, saljsNy);
        if (cachad.isPresent()) return cachad;

        String sokord = sokord(bilnamn);
        List<JsonNode> nya = blocket.elbilsannonser(sokord, BlocketUsedPriceClient.SALES_FORM_NY);
        List<JsonNode> beg = blocket.elbilsannonser(sokord, BlocketUsedPriceClient.SALES_FORM_BEGAGNAD);
        if (nya == null || beg == null) return Optional.empty();

        Annonser a = new Annonser(nya, beg, System.currentTimeMillis());
        if (cache.size() >= CACHE_MAX) cache.clear();
        cache.put(sokord, a);
        Marknadsbild bild = berakna(bilnamn, saljsNy, a.nya(), a.begagnade(), a.tid());
        log.info("marknad: {} (sök \"{}\") — ny {} st, median {} kr; begagnad {} st, median {} kr",
                bilnamn, sokord, bild.ny().antal(), bild.ny().medianKr(),
                bild.begagnad().antal(), bild.begagnad().medianKr());
        return Optional.of(bild);
    }

    /** Bara det som redan finns i cachen — för AI-prompten, som inte ska vänta på Blocket i onödan. */
    public Optional<Marknadsbild> cachad(String bilnamn, boolean saljsNy) {
        if (bilnamn == null) return Optional.empty();
        Annonser a = cache.get(sokord(bilnamn));
        if (a == null || System.currentTimeMillis() - a.tid() > CACHE_TTL_MS) return Optional.empty();
        return Optional.of(berakna(bilnamn, saljsNy, a.nya(), a.begagnade(), a.tid()));
    }

    /**
     * Så många år utan en enda annons som räknas som ett generationsskifte under samma namn.
     * BMW i3: den gamla tillverkades till 2022, den nya kom 2026 — mätt 2026-10-01.
     */
    static final int GENERATIONSGLAPP_AR = 3;

    private record Fynd(int pris, Integer ar) {}

    /**
     * Räknar fram marknadsbilden ur två annonslistor. Paketsynlig så den går att pröva utan HTTP.
     *
     * <p><b>Samma namn kan vara två olika bilar.</b> Mätt 2026-10-01 visade "BMW i3 120 Ah" —
     * den gamla, nedlagd 2022 — nypriset 775 tkr, för handlarna säljer nästa generations i3
     * (årsmodell 2026–2027) under samma modellnamn. Databasen skiljer dem åt: en bil som inte
     * längre säljs har inget pris ({@code saljsNy} falsk), det har bara den nya. Därför:
     * <ul>
     *   <li>Säljs bilen inte ny visas inget nypris alls — det hade gällt en annan bil.</li>
     *   <li>Finns ett glapp på minst {@link #GENERATIONSGLAPP_AR} år mellan de gamla annonserna
     *       och de nya bilarna till salu delas annonserna där. En bil som inte säljs ny får den
     *       äldre sidan, en som säljs ny den nyare.</li>
     * </ul>
     * Utan nya bilar till salu görs ingen delning: glesa begagnatannonser (2014, sedan 2019)
     * är inget generationsskifte, och då finns inget att skilja den gamla bilen från.
     */
    static Marknadsbild berakna(String bilnamn, boolean saljsNy, List<JsonNode> nya,
                                List<JsonNode> begagnade, long hamtadMs) {
        String modell = null;
        List<Fynd> nyFynd = new ArrayList<>();
        for (JsonNode a : nya) {
            if (!sammaModell(bilnamn, a)) continue;
            Integer pris = pris(a, NY_LAGSTA_PRIS_KR);
            if (pris == null || mil(a) > NY_MAX_MIL) continue;
            nyFynd.add(new Fynd(pris, ar(a)));
            if (modell == null) modell = visningsnamn(a);
        }
        List<Fynd> begFynd = new ArrayList<>();
        for (JsonNode a : begagnade) {
            if (!sammaModell(bilnamn, a)) continue;
            Integer pris = pris(a, BEGAGNAD_LAGSTA_PRIS_KR);
            // Saknad mätarställning på en begagnad bil går inte att pröva mot milgränsen.
            if (pris == null || !a.path("mileage").isNumber() || mil(a) > BEGAGNAD_MAX_MIL) continue;
            begFynd.add(new Fynd(pris, ar(a)));
            if (modell == null) modell = visningsnamn(a);
        }

        Integer grans = generationsgrans(nyFynd, begFynd);
        if (grans != null) {
            nyFynd.removeIf(f -> f.ar() != null && (f.ar() >= grans) != saljsNy);
            begFynd.removeIf(f -> f.ar() != null && (f.ar() >= grans) != saljsNy);
        }
        if (!saljsNy) nyFynd.clear();
        return new Marknadsbild(bilnamn, modell, prisdel(nyFynd), prisdel(begFynd), hamtadMs);
    }

    /**
     * Första årsmodellen i den nyare generationen, eller null när inget skifte syns: det SENASTE
     * glappet på minst {@link #GENERATIONSGLAPP_AR} år före den äldsta nya bilen till salu. Det
     * senaste och inte det största: begagnade i3:or har både 2015→2019 och 2022→2026, och det är
     * glappet närmast de nya bilarna som är generationsskiftet.
     */
    static Integer generationsgrans(List<Fynd> nya, List<Fynd> begagnade) {
        java.util.TreeSet<Integer> nyAr = new java.util.TreeSet<>();
        nya.forEach(f -> { if (f.ar() != null) nyAr.add(f.ar()); });
        if (nyAr.isEmpty()) return null;
        java.util.TreeSet<Integer> allaAr = new java.util.TreeSet<>(nyAr);
        begagnade.forEach(f -> { if (f.ar() != null) allaAr.add(f.ar()); });

        Integer grans = null;
        Integer forra = null;
        for (int ar : allaAr) {
            if (ar > nyAr.first()) break;
            if (forra != null && ar - forra >= GENERATIONSGLAPP_AR) grans = ar;
            forra = ar;
        }
        return grans;
    }

    private static Prisdel prisdel(List<Fynd> fynd) {
        if (fynd.isEmpty()) return Prisdel.tom();
        List<Integer> p = new ArrayList<>(fynd.stream().map(Fynd::pris).toList());
        Collections.sort(p);
        List<Integer> ar = fynd.stream().map(Fynd::ar).filter(java.util.Objects::nonNull).toList();
        Integer fran = ar.isEmpty() ? null : Collections.min(ar);
        Integer till = ar.isEmpty() ? null : Collections.max(ar);
        return new Prisdel(p.size(), p.get(p.size() / 2), p.get(0), fran, till);
    }

    private static Integer ar(JsonNode annons) {
        return annons.path("year").isNumber() ? annons.path("year").asInt(0) : null;
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
        if (!"el".equalsIgnoreCase(annons.path("fuel").asString("").trim())) return false;
        String make = norm(annons.path("make").asString(""));
        String model = norm(annons.path("model").asString(""));
        if (model.isEmpty()) return false;
        String annonsNamn = model.equals(make) || model.startsWith(make + " ") ? model : make + " " + model;

        String bil = norm(bilnamn);
        // "MG MG4 XPOWER": databasen skriver märket både fristående och i modellen.
        if (annonsNamn.equals(model) && !make.isEmpty() && bil.startsWith(make + " " + make)) {
            bil = bil.substring(make.length() + 1);
        }
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
        return blocketsMarke(modellOrd(bilnamn));
    }

    private static String modellOrd(String bilnamn) {
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

    /**
     * Märken som Blocket stavar annorlunda i fritexten. Mätt 2026-10-01: "Lynk&Co 02" gav 2
     * annonser, "Lynk & Co 02" gav 9.
     */
    private static String blocketsMarke(String sokord) {
        return sokord.replaceFirst("(?i)^lynk\\s*&\\s*co\\b", "Lynk & Co");
    }

    private static boolean harSiffra(String s) {
        return s.chars().anyMatch(Character::isDigit);
    }

    private static Integer pris(JsonNode annons, int lagsta) {
        JsonNode p = annons.path("price").path("amount");
        return p.isNumber() && p.asInt(0) >= lagsta ? p.asInt(0) : null;
    }

    private static int mil(JsonNode annons) {
        return annons.path("mileage").asInt(0);
    }

    private static String visningsnamn(JsonNode annons) {
        return (annons.path("make").asString("") + " " + annons.path("model").asString("")).trim();
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
