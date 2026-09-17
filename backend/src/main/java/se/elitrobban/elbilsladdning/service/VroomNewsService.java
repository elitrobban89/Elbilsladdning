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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Vrooms pressrum som en månadskarusell.
 *
 * <p>Källan är Mynewsdesks RSS-flöde för vroom Stockholm AB, inte de två webbsidorna:
 * {@code web.vroom.nu} är WordPress men lägger sina inlägg i en egen posttyp som
 * {@code /wp-json/wp/v2/posts} svarar tomt på, och pressrumssidan är renderad HTML vars
 * markup kan byta form vilken natt som helst — det har hänt tre gånger i systerprojekten
 * (auto-data 2026-08-14 och 2026-09-13, bilweb 2026-09-17). RSS är ett kontrakt: fälten
 * {@code title}, {@code pubDate}, {@code description} och {@code link} står där för att
 * läsas maskinellt.
 *
 * <p><b>"Senaste månaden" räknas ur flödet, inte ur kalendern.</b> Vroom publicerar två till
 * fyra gånger i månaden, och den 1:a i en ny månad finns ännu ingenting — en karusell som
 * frågade efter "denna månad" hade då stått tom i en vecka varje månad. Månaden är därför
 * den nyaste post som FINNS, och räcker den inte till {@link #MINSTA_ANTAL} slides fylls
 * listan på bakåt. Varje rad bär sitt eget datum, så en påfylld karusell ljuger inte om när
 * något skrevs, och etiketten blir ett spann så snart två månader är med.
 *
 * <p>Cachen ligger i minnet och inte i databasen med flit: flödet är alltid tillgängligt, och
 * en tabell hade bara gett en andra sanning att hålla i synk. <b>Ett misslyckat anrop cachas
 * aldrig</b> — då serveras förra lyckade hämtningen vidare och nästa anrop får försöka igen.
 * Att spara ett tomt svar hade parkerat karusellen i sex timmar på ett nätverksfel.
 */
@Service
public class VroomNewsService {

    private static final Logger log = LoggerFactory.getLogger(VroomNewsService.class);

    /** Mynewsdesk-flödet för vroom Stockholm AB (pressrummets eget current_news-id). */
    @Value("${vroom.rss.url:https://www.mynewsdesk.com/se/rss/current_news/51204}")
    private String rssUrl;

    /** Så många slides ska karusellen helst ha innan den fylls på med föregående månad. */
    static final int MINSTA_ANTAL = 3;

    /** Aldrig fler än så: prickraden radbryts visserligen, men tjugo prickar läser som en lista. */
    static final int HOGSTA_ANTAL = 8;

    private static final Duration CACHE_TTL = Duration.ofHours(6);

    public static final String KALL_LANK = "https://www.mynewsdesk.com/se/vroom";

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ElbilsladdningBot/1.0; +https://elbilsladdning.onrender.com)";

    private static final String[] MANADER = {
            "januari", "februari", "mars", "april", "maj", "juni",
            "juli", "augusti", "september", "oktober", "november", "december"};

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile Nyheter cache;
    private volatile long cachadVid;

    /** En pressrelease, tillplattad till det karusellen visar. */
    public record Nyhet(String rubrik, String sammanfattning, String lank, String datum, String manad) {}

    /**
     * @param manad   etiketten över karusellen, t.ex. "september 2026" eller "augusti–september 2026"
     * @param kalla   namnet som ska stå som källa — alltid "Vroom"
     * @param nyheter raderna, nyast först
     */
    public record Nyheter(String manad, String kalla, String kallLank, List<Nyhet> nyheter) {}

    /** Senaste månadens nyheter, ur cachen när den är färsk. */
    public Nyheter senaste() {
        Nyheter cachad = cache;
        if (cachad != null && System.currentTimeMillis() - cachadVid < CACHE_TTL.toMillis()) return cachad;
        try {
            Nyheter fardig = gruppera(hamta());
            if (!fardig.nyheter().isEmpty()) {
                cache = fardig;
                cachadVid = System.currentTimeMillis();
                return fardig;
            }
            // Tomt flöde är inget svar att spara — se klassens javadoc.
            log.warn("Vroom-flödet svarade utan poster");
        } catch (Exception e) {
            log.warn("Kunde inte hämta Vroom-flödet: {}", e.toString());
        }
        return cachad != null ? cachad : new Nyheter(null, "Vroom", KALL_LANK, List.of());
    }

    /** Alla poster i flödet, nyast först. Paketprivat för provet. */
    List<Nyhet> hamta() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(rssUrl))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> svar = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (svar.statusCode() != 200) throw new IllegalStateException("HTTP " + svar.statusCode());
        return parse(svar.body());
    }

    /**
     * RSS till rader. Poster utan läsbart {@code pubDate} hoppas över: datumet är det enda som
     * avgör vilken månad raden hör till, och en rad utan månad kan inte placeras i karusellen.
     */
    static List<Nyhet> parse(String xml) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        List<Nyhet> rader = new ArrayList<>();
        for (Element item : doc.select("item")) {
            Element rubrikEl = item.selectFirst("title");
            Element datumEl = item.selectFirst("pubDate");
            if (rubrikEl == null || datumEl == null) continue;
            String rubrik = rubrikEl.text().trim();
            String datumText = datumEl.text().trim();
            if (rubrik.isBlank() || datumText.isBlank()) continue;
            ZonedDateTime nar = tolkaDatum(datumText);
            if (nar == null) continue;
            // description är fritext hos Mynewsdesk och har burit både taggar och rena entiteter.
            // Jsoup.parse().text() ger ren text oavsett vilket, och frontenden escapar dessutom.
            Element textEl = item.selectFirst("description");
            String sammanfattning = textEl == null ? "" : Jsoup.parse(textEl.text()).text().trim();
            Element lankEl = item.selectFirst("link");
            String lank = lankEl == null ? "" : utanSparning(lankEl.text().trim());
            rader.add(new Nyhet(rubrik, sammanfattning, lank,
                    nar.toLocalDate().toString(), manadsEtikett(nar)));
        }
        rader.sort((a, b) -> b.datum().compareTo(a.datum()));
        return rader;
    }

    /**
     * Raderna ur den nyaste månaden — påfyllda bakåt tills de är {@link #MINSTA_ANTAL}.
     *
     * <p>Etiketten följer med påfyllningen: hämtas rader ur två månader står båda i rubriken.
     * Att skriva "september" över en rad från augusti hade varit fel om än bara med en rad.
     */
    static Nyheter gruppera(List<Nyhet> rader) {
        if (rader.isEmpty()) return new Nyheter(null, "Vroom", KALL_LANK, List.of());
        String nyasteManad = rader.get(0).manad();
        List<Nyhet> valda = new ArrayList<>(rader.stream().filter(r -> r.manad().equals(nyasteManad)).toList());
        for (Nyhet r : rader) {
            if (valda.size() >= MINSTA_ANTAL) break;
            if (!valda.contains(r)) valda.add(r);
        }
        if (valda.size() > HOGSTA_ANTAL) valda = new ArrayList<>(valda.subList(0, HOGSTA_ANTAL));
        String aldsta = valda.get(valda.size() - 1).manad();
        String etikett = aldsta.equals(nyasteManad) ? nyasteManad : kortaSpann(aldsta, nyasteManad);
        return new Nyheter(etikett, "Vroom", KALL_LANK, List.copyOf(valda));
    }

    /** "augusti 2026" + "september 2026" blir "augusti–september 2026"; olika år behåller båda. */
    private static String kortaSpann(String aldsta, String nyaste) {
        String[] a = aldsta.split(" ");
        String[] n = nyaste.split(" ");
        if (a.length == 2 && n.length == 2 && a[1].equals(n[1])) return a[0] + "–" + n[0] + " " + n[1];
        return aldsta + "–" + nyaste;
    }

    /**
     * {@code pubDate} till tidpunkt — <b>utan att lita på veckodagen</b>.
     *
     * <p>{@link DateTimeFormatter#RFC_1123_DATE_TIME} validerar veckodagen och KASTAR när den
     * inte stämmer med datumet. En felstavad veckodag i flödet hade alltså tagit bort posten
     * helt, tyst, och en karusell som saknar den nyaste raden ser ut precis som en karusell
     * som är i fas. Veckodagen bär ingen information vi använder — månaden och datumet gör
     * det — så den strippas och resten tolkas om när den strikta vägen säger nej.
     */
    static ZonedDateTime tolkaDatum(String text) {
        try {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (Exception forsta) {
            int komma = text.indexOf(',');
            String utanVeckodag = komma < 0 ? text : text.substring(komma + 1).trim();
            try {
                return ZonedDateTime.parse(utanVeckodag, UTAN_VECKODAG);
            } catch (Exception andra) {
                return null;
            }
        }
    }

    private static final DateTimeFormatter UTAN_VECKODAG =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", java.util.Locale.ENGLISH);

    static String manadsEtikett(ZonedDateTime nar) {
        return MANADER[nar.getMonthValue() - 1] + " " + nar.getYear();
    }

    /** Mynewsdesks utm-parametrar hör till flödet, inte till länken en läsare ska klicka på. */
    static String utanSparning(String lank) {
        int fraga = lank.indexOf('?');
        return fraga < 0 ? lank : lank.substring(0, fraga);
    }
}
