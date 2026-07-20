package se.elitrobban.elbilsladdning.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.elitrobban.elbilsladdning.model.EvSalesRankEntry;
import se.elitrobban.elbilsladdning.repository.EvSalesRankRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hämtar "De 10 populäraste elbilarna i Sverige"-inlägget från elbilsvaruhuset.se (WordPress,
 * öppet REST-API, källa Mobility Sweden) och lagrar rankingtabellen. Till skillnad från
 * carla.se/elbilsindex (Vercel-bot-spärr, blockerar all automatisk hämtning) är denna sida
 * fritt tillgänglig och listan renodlat BEV — ingen laddhybrid-/bensinblandning.
 *
 * Tabellen ersätts i sin helhet varje körning (delete + save) — ingen historik sparas,
 * bara senaste kända ranking.
 */
@Service
public class EvSalesRankSyncService {

    private static final Logger log = LoggerFactory.getLogger(EvSalesRankSyncService.class);

    @Value("${elbilsvaruhuset.api.url:https://elbilsvaruhuset.se/wp-json/wp/v2/posts?slug=popularaste-elbilarna&_fields=id,content,modified}")
    private String apiUrl;

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; ElbilsladdningBot/1.0; +https://elbilsladdning.onrender.com)";

    private final EvSalesRankRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public EvSalesRankSyncService(EvSalesRankRepository repo) {
        this.repo = repo;
    }

    /** Hämtar senaste inlägget och ersätter rankingtabellen. Returnerar körsammanfattning. */
    @Transactional
    public Map<String, Object> syncNow() {
        try {
            String json = fetchJson(apiUrl);
            JsonNode arr = mapper.readTree(json);
            if (!arr.isArray() || arr.isEmpty())
                return Map.of("status", "ERROR", "error", "Hittade inget inlägg med slug popularaste-elbilarna");

            String html = arr.get(0).path("content").path("rendered").asText("");
            List<EvSalesRankEntry> rows = parse(html);
            if (rows.isEmpty())
                return Map.of("status", "ERROR", "error", "Kunde inte tolka rankingtabellen ur sidan");

            repo.deleteAll();
            repo.saveAll(rows);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "OK");
            result.put("imported", rows.size());
            result.put("period", rows.get(0).getPeriodLabel());
            result.put("top3", rows.stream().limit(3)
                    .map(r -> r.getRank() + ". " + r.getModel() + " (" + r.getUnits() + " st)").toList());
            log.info("elbilsvaruhuset sync: {} modeller, period {}", rows.size(), rows.get(0).getPeriodLabel());
            return result;
        } catch (Exception e) {
            log.error("elbilsvaruhuset sync misslyckades: {}", e.getMessage(), e);
            return Map.of("status", "ERROR", "error", String.valueOf(e.getMessage()));
        }
    }

    // ── Parsning (paketprivat/statiskt så tester kan köra utan HTTP) ───────────

    /**
     * Läser rankingtabellen ur post-HTML:n. Går via riktiga table/td-celler (inte flödestext)
     * eftersom modellnamn som "Polestar 4" innehåller siffror — går inte att skilja från
     * antalskolumnen med regex på plattad text.
     */
    static List<EvSalesRankEntry> parse(String html) {
        Document doc = Jsoup.parse(html);
        Element scope = doc.select("figure.wp-block-table").first();
        Element table = (scope != null ? scope : doc).select("table").first();
        if (table == null) return List.of();

        String periodLabel = extractPeriod(scope != null ? scope : doc);

        List<EvSalesRankEntry> out = new ArrayList<>();
        for (Element tr : table.select("tbody tr")) {
            Elements td = tr.select("td");
            if (td.size() < 4) continue;
            Integer rank = parseInt(td.get(0).text());
            String model = td.get(1).text().trim();
            Integer units = parseInt(td.get(2).text());
            Integer priceKr = parseInt(td.get(3).text());
            if (rank == null || model.isBlank() || units == null) continue;
            out.add(new EvSalesRankEntry(rank, model, units, priceKr, periodLabel));
        }
        return out;
    }

    private static String extractPeriod(Element scope) {
        Element caption = scope.select("figcaption").first();
        if (caption == null) return null;
        Matcher m = Pattern.compile("Mobility Sweden\\s*,\\s*([^.]+)").matcher(caption.text());
        return m.find() ? m.group(1).trim() : null;
    }

    private static Integer parseInt(String s) {
        String digits = s.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.parseInt(digits);
    }

    // ── Hämtning (paketprivat så tester kan stubba via apiUrl) ────────────────

    String fetchJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode() + " för " + url);
        return resp.body();
    }
}
