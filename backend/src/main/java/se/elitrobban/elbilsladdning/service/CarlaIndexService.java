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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Carlas Elbilsindex — månadens begagnade elbilsmarknad — ur Carlas pressrum.
 *
 * <p><b>Varför pressrummet och inte carla.se.</b> Hela carla.se ligger bakom Vercels
 * säkerhetskontroll (HTTP 429 med en JavaScript-utmaning, uppmätt 2026-09-27 på varje sida, även
 * via en hämtningstjänst), så siffrorna klistrades in för hand en gång i månaden. Samma siffror
 * publiceras som pressmeddelande via Cision, och Cisions RSS-flöde är öppet — samma lösning som
 * Vroom via Mynewsdesk. Ett flöde är ett kontrakt; en webbsida bakom en bot-vägg är det inte.
 *
 * <p>Bara poster vars rubrik innehåller "Elbilsindex" räknas — pressrummet bär också nyheter om
 * bolaget (återförsäljaravtal, serviceavtal) som inte hör hemma i en faktakarusell. Antal och
 * medianpris läses ur ingressen; saknas antalet används posten ändå, men då bara med rubriken.
 *
 * <p>Cachen är sex timmar och ett misslyckat anrop cachas aldrig, precis som {@link VroomNewsService}.
 *
 * @author Robert Andersson Kopler
 */
@Service
public class CarlaIndexService {

    private static final Logger log = LoggerFactory.getLogger(CarlaIndexService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ElbilsladdningBot/1.0; +https://elbilsladdning.onrender.com)";

    /** "Carlas Elbilsindex augusti 2026: Tesla och Volvo står för ..." */
    private static final Pattern RUBRIK = Pattern.compile("Elbilsindex\\s+(\\p{L}+\\s+\\d{4})\\s*[:–-]\\s*(.+)");
    /** "såldes 7 246 begagnade elbilar" / "registrerades 9 673 försäljningar av begagnade elbilar" */
    private static final Pattern ANTAL = Pattern.compile(
            "(\\d{1,3}(?:[ \\u00a0\\u202f]\\d{3})*)\\s+(?:försäljningar av\\s+)?begagnade elbilar");
    /** "medianpriset steg till 349 900 kronor" */
    private static final Pattern MEDIANPRIS = Pattern.compile(
            "[Mm]edianpris(?:et)?[^.]{0,80}?(\\d{3}[ \\u00a0\\u202f]\\d{3})\\s*kronor");

    @Value("${carla.rss.url:https://news.cision.com/se/carla-ab/rss/latest}")
    private String rssUrl = "https://news.cision.com/se/carla-ab/rss/latest";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile Index cache;
    private volatile long cachadVid;

    /**
     * @param manad        "augusti 2026"
     * @param rubrik       Carlas egen rubrik efter kolonet
     * @param antal        sålda begagnade elbilar under månaden, eller null
     * @param medianprisKr medianpriset i kronor, eller null
     */
    public record Index(String manad, String rubrik, Integer antal, Integer medianprisKr,
                        String lank, String datum, String kalla) {}

    /** Senaste månadens index, eller null om flödet inte svarat och ingenting finns cachat. */
    public Index senaste() {
        Index cachad = cache;
        if (cachad != null && System.currentTimeMillis() - cachadVid < CACHE_TTL.toMillis()) return cachad;
        try {
            Index nytt = parse(hamta());
            if (nytt != null) {
                cache = nytt;
                cachadVid = System.currentTimeMillis();
                return nytt;
            }
            log.warn("Carlas pressflöde svarade utan någon Elbilsindex-post");
        } catch (Exception e) {
            log.warn("Kunde inte hämta Carlas pressflöde: {}", e.toString());
        }
        return cachad;
    }

    private String hamta() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(rssUrl))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> svar = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (svar.statusCode() != 200) throw new IllegalStateException("HTTP " + svar.statusCode());
        return svar.body();
    }

    /** Nyaste Elbilsindex-posten i flödet, eller null. Paketprivat för provet. */
    static Index parse(String xml) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        Index nyast = null;
        ZonedDateTime nyastNar = null;
        for (Element item : doc.select("item")) {
            Element titel = item.selectFirst("title");
            Element datum = item.selectFirst("pubDate");
            if (titel == null || datum == null) continue;
            Matcher r = RUBRIK.matcher(titel.text().trim());
            if (!r.find()) continue;
            ZonedDateTime nar = VroomNewsService.tolkaDatum(datum.text().trim());
            if (nar == null || (nyastNar != null && !nar.isAfter(nyastNar))) continue;
            Element beskr = item.selectFirst("description");
            String text = beskr == null ? "" : Jsoup.parse(beskr.text()).text();
            Element lank = item.selectFirst("link");
            nyast = new Index(r.group(1).toLowerCase(), r.group(2).trim(), tal(ANTAL, text), tal(MEDIANPRIS, text),
                    lank == null ? "" : lank.text().trim(), nar.toLocalDate().toString(), "Carlas Elbilsindex");
            nyastNar = nar;
        }
        return nyast;
    }

    private static Integer tal(Pattern p, String text) {
        Matcher m = p.matcher(text);
        if (!m.find()) return null;
        try {
            return Integer.parseInt(m.group(1).replaceAll("[ \\u00a0\\u202f]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
