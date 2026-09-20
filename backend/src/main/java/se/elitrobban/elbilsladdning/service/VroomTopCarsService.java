package se.elitrobban.elbilsladdning.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Månadens tio mest registrerade elbilar i Sverige — siffrorna kommer från Vroom.
 *
 * <p><b>Varför flödet är YouTube och inte vroom.nu.</b> Vrooms egna kanaler ger marknaden i
 * AGGREGAT: pressreleasen "Bilmarknaden &lt;månad&gt;" skriver ut andelar och tillväxttal,
 * och modell-topplistan ligger i deras betalda tjänst. Kontrollerat 2026-09-17 mot både
 * pressrummet och {@code web.vroom.nu} — ordet "topplista" står där sex gånger, men varje
 * gång som en beskrivning av vad kunden får i verktyget, aldrig som en lista på sidan.
 * Studio Esse publicerar däremot listan i klartext varje månad med <b>Vroom som uttalad
 * källa</b>, och YouTubes kanalflöde bär hela videobeskrivningen i {@code media:description}.
 * Det är alltså Vrooms siffror, hämtade där de faktiskt går att läsa maskinellt.
 *
 * <p><b>Listan är elbilar, trots att källan skriver "personbilar".</b> Rubriken i appen säger
 * därför <i>elbilar</i>, och det är avgjort på bevis, inte på en gissning:
 *
 * <ul>
 *   <li><b>Det avgörande:</b> Sveriges faktiska storsäljare SAKNAS HELT. Volvo XC60, Toyota
 *       Yaris Cross och VW Golf finns inte på någon av de 25 platserna. En lista över alla
 *       personbilar utan XC60 är inte en lista över alla personbilar.</li>
 *   <li>Samtliga 25 rader är modeller som säljs som batterielbil, och flera av dem
 *       (EV2/EV3/EV5/EV9, ID.4/ID.7, EX30/EX40/EC40, iX1/iX3, Enyaq, MG4, bZ4X, Q4 e-tron,
 *       Renault 5, Polestar, Cupra Raval) finns inte ens i någon annan drivlina. Att
 *       Toyota skrivs som <i>C-HR+</i> och inte C-HR pekar åt samma håll: plusset ÄR
 *       elversionen.</li>
 *   <li>Storleken stämmer: en etta på 731 bilar kan inte vara en marknad på ~20 000 i
 *       månaden, och Vrooms egen augustirapport säger att elbilar stod för 46 % av
 *       nyregistreringarna.</li>
 * </ul>
 *
 * Skulle källan en månad börja blanda in förbränningsbilar blir rubriken fel, och det är den
 * enda vägen den kan bli fel: talen kommer aldrig härifrån utan alltid ur beskrivningen.
 * Kontrollen är billig — dyker XC60, Golf eller Yaris Cross upp i listan har innebörden
 * ändrats och rubriken måste följa med.
 *
 * <p>Hittas ingen post med en läsbar lista returneras en tom lista och frontenden ritar ingen
 * flik. <b>Ett misslyckande cachas aldrig</b> — samma linje som {@link VroomNewsService}.
 *
 * @author Robert Andersson Kopler
 */
@Service
public class VroomTopCarsService {

    private static final Logger log = LoggerFactory.getLogger(VroomTopCarsService.class);

    /** Studio Esses kanalflöde. Kanal-id:t är stabilt; @-namnet kan bytas när som helst. */
    @Value("${vroom.topcars.rss.url:https://www.youtube.com/feeds/videos.xml?channel_id=UCYYC16tUQxICZIfqrbfjPfw}")
    private String rssUrl;

    /** Så många rader visas. Källan ger 25; tio är vad en karusellruta rymmer läsbart. */
    static final int TOPP_ANTAL = 10;

    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ElbilsladdningBot/1.0; +https://elbilsladdning.onrender.com)";

    /**
     * Rubrikraden, t.ex. "Topp 25 personbilar, Augusti 2026:". Antalet läses inte — vi tar
     * alltid de tio första raderna — men det måste STÅ där, annars är det en annan sorts video.
     */
    private static final Pattern RUBRIK = Pattern.compile(
            "Topp\\s+\\d+\\s+personbilar,\\s*([A-Za-zÅÄÖåäöé]+)\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);

    /**
     * En rad: "1. EX40 (Volvo) — 731 st".
     *
     * <p>Tankstrecket är em-dash (U+2014) i dagens beskrivningar, men en beskrivning skrivs för
     * hand varje månad: en bindestreckstangent i stället för em-dash hade fällt hela listan.
     * Därför godtas {@code — – -} och alla tre sorters mellanslag runt talet, inklusive det
     * hårda (U+00A0) och det smala (U+202F) som ordbehandlare gärna smyger in.
     *
     * <p><b>Ingen radankare.</b> Mönstret satt först på {@code (?m)^} och matchade då ingenting
     * alls, för {@code Element.text()} plattar ut radbrytningar till mellanslag — hela listan
     * blev en enda rad. Texten läses nu med {@code wholeText()}, men ankaret är ändå borta:
     * kravet att modellnamnet följs av "(märke) — N st" är strängare än ett radbyte, och en
     * lista som en dag levereras utplattad ska fortfarande gå att läsa.
     */
    private static final Pattern RAD = Pattern.compile(
            "(\\d{1,2})\\.\\s*([^()\\r\\n]+?)\\s*\\(([^)]+)\\)\\s*[\\u2014\\u2013-]\\s*"
            + "([\\d\\s\\u00a0\\u202f]+?)\\s*st\\b");

    private static final String[] MANADER = {
            "januari", "februari", "mars", "april", "maj", "juni",
            "juli", "augusti", "september", "oktober", "november", "december"};

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile Topplista cache;
    private volatile long cachadVid;

    /** En rad i topplistan. {@code antal} är nyregistreringar under månaden. */
    public record Bil(int plats, String modell, String marke, int antal) {}

    /**
     * @param manad    "augusti 2026" — månaden siffrorna gäller, inte månaden de publicerades
     * @param kalla    "Vroom", som är där siffrorna kommer ifrån
     * @param sammanstalltAv den som publicerar listan, för hederlig attribution
     * @param lank     videon listan är hämtad ur
     * @param bilar    tio rader, plats 1 först
     */
    public record Topplista(String manad, String kalla, String sammanstalltAv, String lank, List<Bil> bilar) {}

    private static final Topplista TOM = new Topplista(null, "Vroom", "Studio Esse", null, List.of());

    /** Senaste månadens topplista, ur cachen när den är färsk. */
    public Topplista senaste() {
        Topplista cachad = cache;
        if (cachad != null && System.currentTimeMillis() - cachadVid < CACHE_TTL.toMillis()) return cachad;
        try {
            Topplista fardig = parse(hamta());
            if (!fardig.bilar().isEmpty()) {
                cache = fardig;
                cachadVid = System.currentTimeMillis();
                return fardig;
            }
            log.warn("Hittade ingen läsbar topplista i kanalflödet");
        } catch (Exception e) {
            log.warn("Kunde inte hämta topplistan: {}", e.toString());
        }
        return cachad != null ? cachad : TOM;
    }

    String hamta() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(rssUrl))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/atom+xml, application/xml")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> svar = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (svar.statusCode() != 200) throw new IllegalStateException("HTTP " + svar.statusCode());
        return svar.body();
    }

    /**
     * Kanalflödet till topplista. Posterna gås igenom i flödets ordning (nyast först) och den
     * FÖRSTA som bär en läsbar lista vinner — kanalen publicerar också bilnyheter och tester,
     * och de saknar rubrikraden.
     */
    static Topplista parse(String xml) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        for (Element entry : doc.select("entry")) {
            Element beskrivning = entry.selectFirst("media|description");
            if (beskrivning == null) continue;
            String text = beskrivning.wholeText();
            Matcher rubrik = RUBRIK.matcher(text);
            if (!rubrik.find()) continue;
            List<Bil> bilar = rader(text);
            if (bilar.size() < TOPP_ANTAL) continue;   // halv lista är ingen lista
            varnaOmListanByttInnebord(bilar);
            Element lank = entry.selectFirst("link");
            return new Topplista(
                    manadsEtikett(rubrik.group(1), rubrik.group(2)),
                    "Vroom", "Studio Esse",
                    lank == null ? null : lank.attr("href"),
                    List.copyOf(bilar.subList(0, TOPP_ANTAL)));
        }
        return TOM;
    }

    /**
     * Modeller som INTE finns som batterielbil. Dyker någon av dem upp i listan har källan
     * slutat mena elbilar, och då är appens rubrik ("Mest registrerade elbilarna") fel.
     *
     * <p>Urvalet är Sveriges faktiska storsäljare utan BEV-version — det är just deras
     * FRÅNVARO som bevisar att dagens lista är elbilar. XC60 finns som laddhybrid men inte
     * som elbil, Golf som allt utom elbil (e-Golf heter e-Golf), Yaris Cross som hybrid.
     */
    private static final List<String> EJ_ELBILSMODELLER = List.of("xc60", "golf", "yaris cross");

    /**
     * Loggar när listans innebörd verkar ha ändrats. <b>Bara en varning</b> — raderna är
     * fortfarande riktiga siffror, och att tiga om dem vore värre än en felaktig rubrik.
     * Vakten finns för att frågan ska nå en människa, inte för att fälla ett svar.
     */
    static void varnaOmListanByttInnebord(List<Bil> bilar) {
        List<String> fynd = bilar.stream()
                .filter(b -> EJ_ELBILSMODELLER.stream()
                        .anyMatch(m -> b.modell().toLowerCase(Locale.ROOT).equals(m)))
                .map(Bil::modell)
                .toList();
        if (!fynd.isEmpty())
            log.warn("Topplistan innehåller {} — modeller som inte finns som elbil. Källan kan ha "
                    + "bytt fran elbilar till alla personbilar; rubriken i appen sager 'elbilar'.",
                    String.join(", ", fynd));
    }

    /** Raderna i beskrivningen, i den ordning de står. Platsnumret läses ur texten, inte ur index. */
    static List<Bil> rader(String text) {
        List<Bil> bilar = new ArrayList<>();
        Matcher m = RAD.matcher(text);
        while (m.find()) {
            int antal;
            try {
                antal = Integer.parseInt(m.group(4).replaceAll("[\\s\\u00a0\\u202f]", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            bilar.add(new Bil(Integer.parseInt(m.group(1)), m.group(2).trim(), m.group(3).trim(), antal));
        }
        return bilar;
    }

    /**
     * "Augusti" + "2026" blir "augusti 2026". Månadsnamnet normaliseras mot vår egen lista i
     * stället för att skrivas av: källan har skrivit både "Augusti" och "augusti", och en
     * rubrik som byter versal mellan månaderna ser ut som ett fel i appen.
     */
    static String manadsEtikett(String manad, String ar) {
        String lower = manad.toLowerCase(Locale.ROOT);
        for (String m : MANADER) if (m.equals(lower)) return m + " " + ar;
        return lower + " " + ar;
    }
}
