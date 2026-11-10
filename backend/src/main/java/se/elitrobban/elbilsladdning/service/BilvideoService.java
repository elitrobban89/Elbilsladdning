package se.elitrobban.elbilsladdning.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import se.elitrobban.elbilsladdning.model.BilvideoSnapshot;
import se.elitrobban.elbilsladdning.repository.BilvideoRepository;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Videokortet i karusellen: Alriks Bilars senaste videor plus ett par handplockade.
 *
 * <p>Flödet är kanalens öppna Atom-flöde, samma väg som {@link VroomTopCarsService} läser
 * Studio Esse — ingen API-nyckel, ingen kvot. Det bär de 15 senaste posterna, och ungefär
 * hälften är shorts (2026-10-30: 7 av 15). De sorteras bort: en 40 sekunders klipp med
 * "#shorts" i titeln är reklam för en video, inte en video att lägga tid på.
 *
 * <p><b>Utvalda videor ligger i koden, inte i flödet.</b> Användaren ville ha med ett
 * familjeelbilstest från 2026-08-31 som för länge sedan fallit ur de 15 senaste, och ett
 * avsnitt av TrafikMagasinet som ligger på en helt annan kanal. Ett flöde kan inte ge någon
 * av dem, så de står i {@link #UTVALDA} med titel och datum utskrivna — och visas efter de
 * senaste, märkta som utvalda, så att det nya alltid kommer först.
 *
 * <p>Fail-soft precis som Vroom-korten: ett misslyckande cachas aldrig, senaste lyckade
 * listan sparas i databasen och visas när flödet inte svarar.
 *
 * @author Robert Andersson Kopler
 */
@Service
public class BilvideoService {

    private static final Logger log = LoggerFactory.getLogger(BilvideoService.class);

    /** Alriks Bilars kanalflöde. Kanal-id:t är stabilt; @-namnet kan bytas när som helst. */
    @Value("${bilvideor.rss.url:https://www.youtube.com/feeds/videos.xml?channel_id=UCU-4UJDGqQWK5Iv4wXDlzGQ}")
    private String rssUrl;

    static final String KANAL = "Alriks Bilar";
    static final String KANAL_LANK = "https://www.youtube.com/@alriksbilar";

    /** Så många av de senaste videorna visas. Fler blir en karusell man aldrig bläddrar igenom. */
    static final int ANTAL_SENASTE = 5;

    private static final Duration CACHE_TTL = Duration.ofHours(6);
    static final Duration RESERV_TTL = Duration.ofMinutes(30);

    private static final ZoneId SVERIGE = ZoneId.of("Europe/Stockholm");

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ElbilsladdningBot/1.0; +https://elbilsladdning.onrender.com)";

    /** YouTubes video-id: elva tecken. Allt annat är inte en video vi kan bygga en länk av. */
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    /**
     * @param id     YouTubes video-id; frontenden bygger miniatyren av det
     * @param titel  videons titel, som kanalen skrev den
     * @param kanal  kanalens namn — utvalda videor kan komma från andra kanaler än Alriks
     * @param datum  publiceringsdag i svensk tid, "2026-10-29"
     * @param utvald handplockad, inte ur flödet
     */
    public record Video(String id, String titel, String kanal, String datum, boolean utvald) {
        public String lank() { return "https://www.youtube.com/watch?v=" + id; }
    }

    /** @param videor de senaste först, sedan de utvalda */
    public record Videolista(String kanal, String kanalLank, List<Video> videor) {}

    /**
     * Handplockade videor (2026-10-30, på användarens begäran). Visas efter flödets senaste.
     * Står en av dem redan bland de senaste visas den bara där. Studio Esses två videor om
     * oktoberförsäljningen lades till 2026-11-10: samma källa som Vroom-flikens topplista, men
     * där står bara de tre första i år, och i videon går hela listan igenom.
     */
    static final List<Video> UTVALDA = List.of(
            new Video("3dHcq44U1q8", "Nu får Volvo lugna ner sig – elbilsförsäljningen oktober 2026",
                    "Studio Esse", "2026-11-04", true),
            new Video("XqZfWbcOSco", "Historiskt! Över hälften av alla nya bilar i Sverige är nu elbilar",
                    "Studio Esse", "2026-11-02", true),
            new Video("z1AENQtVddg", "Test: BÄSTA FAMILJE-ELBILEN? Uppdaterad version med ny ekonomirond.",
                    "Alriks Bilar", "2026-08-31", true),
            new Video("G4SfUar-MVw", "Allt fler upptäcker fördelarna med elbilar! Perstad Presenterar: TrafikMagasinet, BilTV med Perstad",
                    "TrafikMagasinet BilTV med Perstad", "2026-10-27", true));

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final BilvideoRepository reserv;
    private final ObjectMapper json = new ObjectMapper();

    private volatile List<Video> cache;
    private volatile long cachadVid;

    public BilvideoService(BilvideoRepository reserv) {
        this.reserv = reserv;
    }

    /** Senaste videorna plus de utvalda. Flödet läses om när cachen åldrats. */
    public Videolista senaste() {
        return new Videolista(KANAL, KANAL_LANK, medUtvalda(senasteUrFlodet()));
    }

    private List<Video> senasteUrFlodet() {
        List<Video> cachad = cache;
        if (cachad != null && System.currentTimeMillis() - cachadVid < CACHE_TTL.toMillis()) return cachad;
        try {
            List<Video> fardig = parse(hamta());
            if (!fardig.isEmpty()) {
                cache = fardig;
                cachadVid = System.currentTimeMillis();
                if (!fardig.equals(cachad)) spara(fardig);
                return fardig;
            }
            log.warn("Alriks Bilars kanalflöde gav inga videor utöver shorts");
        } catch (Exception e) {
            log.warn("Kunde inte hämta Alriks Bilars kanalflöde: {}", e.toString());
        }
        if (cachad != null) return cachad;
        List<Video> sparad = lasSparad();
        if (sparad == null) return List.of();
        // Reserven cachas, men kort: annars provas flödet på varje anrop medan det är nere.
        cache = sparad;
        cachadVid = System.currentTimeMillis() - CACHE_TTL.toMillis() + RESERV_TTL.toMillis();
        return sparad;
    }

    static List<Video> medUtvalda(List<Video> senaste) {
        List<Video> ut = new ArrayList<>(senaste);
        for (Video v : UTVALDA) {
            if (senaste.stream().noneMatch(s -> s.id().equals(v.id()))) ut.add(v);
        }
        return List.copyOf(ut);
    }

    private void spara(List<Video> videor) {
        try {
            reserv.save(new BilvideoSnapshot(json.writeValueAsString(videor), Instant.now()));
        } catch (Exception e) {
            log.warn("Kunde inte spara videolistan som reserv: {}", e.toString());
        }
    }

    private List<Video> lasSparad() {
        try {
            return reserv.findById(BilvideoSnapshot.ENDA_ID)
                    .map(rad -> {
                        log.info("Visar videolistan sparad {} — kanalflödet svarar inte", rad.getSparadVid());
                        return List.of(json.readValue(rad.getJson(), Video[].class));
                    })
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Kunde inte läsa den sparade videolistan: {}", e.toString());
            return null;
        }
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
     * Kanalflödet till de {@link #ANTAL_SENASTE} senaste vanliga videorna, nyast först.
     *
     * <p>En short känns igen på två sätt, och båda räcker: länken går till {@code /shorts/}
     * i stället för {@code /watch}, eller titeln bär {@code #shorts}. Länken är det säkra
     * tecknet — titeltaggen är kanalens egen vana och kan utebli.
     */
    static List<Video> parse(String xml) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        List<Video> ut = new ArrayList<>();
        for (Element entry : doc.select("entry")) {
            Element idEl = entry.selectFirst("yt|videoId");
            Element titelEl = entry.selectFirst("title");
            Element publicerad = entry.selectFirst("published");
            Element lank = entry.selectFirst("link[rel=alternate]");
            if (idEl == null || titelEl == null || publicerad == null) continue;

            String id = idEl.text().trim();
            String titel = titelEl.text().trim();
            if (!VIDEO_ID.matcher(id).matches() || titel.isEmpty()) continue;
            if (lank != null && lank.attr("href").contains("/shorts/")) continue;
            if (titel.toLowerCase().contains("#shorts")) continue;

            String datum;
            try {
                datum = OffsetDateTime.parse(publicerad.text().trim())
                        .atZoneSameInstant(SVERIGE).toLocalDate().toString();
            } catch (Exception e) {
                continue;
            }
            ut.add(new Video(id, titel, KANAL, datum, false));
            if (ut.size() == ANTAL_SENASTE) break;
        }
        return ut;
    }
}
