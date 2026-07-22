package se.elitrobban.elbilsladdning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GroqService {

    private static final String MODEL     = "openai/gpt-oss-20b";
    private static final int CHAT_MAX_HISTORY = 8;
    private static final long   CACHE_TTL = 30 * 60 * 1000L;

    @Value("${groq.api.key}")
    private String apiKey;

    // Överstyrbar i tester — pekas mot en lokal stubbserver
    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqUrl;

    private final RestClient http = RestClient.create();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private record CacheEntry(GroqResult result, long timestamp) {}
    private final Map<String, CacheEntry> recommendCache = new ConcurrentHashMap<>();
    private volatile long quotaExceededUntil = 0;

    public record GroqResult(String recommendation, String funFact) {}

    public boolean isQuotaExceeded() {
        return System.currentTimeMillis() < quotaExceededUntil;
    }

    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> history, List<CarSpec> cars, String stationContext) {
        if (isQuotaExceeded()) return "AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!";

        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        String sysPrompt = buildChatSystemPrompt(cars);
        if (stationContext != null && !stationContext.isBlank())
            sysPrompt += "\n\nAktuella laddstationer i sökningen:\n" + stationContext;
        messages.add(Map.of("role", "system", "content", sysPrompt));
        List<Map<String, String>> trimmed = history.size() > CHAT_MAX_HISTORY
                ? history.subList(history.size() - CHAT_MAX_HISTORY, history.size()) : history;
        trimmed.forEach(m -> messages.add(Map.of("role", (Object) m.get("role"), "content", (Object) m.get("content"))));

        Map<String, Object> body = Map.of(
                "model", MODEL, "max_tokens", 800, "temperature", 0.7,
                "reasoning_effort", "low",
                "messages", messages);
        try {
            Map<String, Object> resp = http.post()
                    .uri(groqUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            var choices = (List<Map<String, Object>>) resp.get("choices");
            var msg     = (Map<String, Object>) choices.get(0).get("message");
            quotaExceededUntil = 0;
            return (String) msg.get("content");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429)
                quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(e.getResponseBodyAsString());
            return "AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!";
        } catch (Exception e) {
            return "Tyvärr kunde jag inte svara just nu. Försök igen!";
        }
    }

    String buildChatSystemPrompt(List<CarSpec> cars) {
        var sb = new StringBuilder();
        sb.append("""
Du är en assistent för en EV-laddningsapp i Sverige.
Du svarar ENDAST på frågor om elbilar, laddning, räckvidd, laddstationer och bilresor.

Om användaren frågar om något annat svarar du:
"Det kan jag inte hjälpa med — jag är specialiserad på elbilar och laddning."

Svara alltid på svenska. Svara kortfattat (max 3–4 meningar) men konkret och specifik med siffror.
Om någon frågar om elbilsköp och inte angett budget, fråga efter det.

PRENUMERATION (svara på detta om användaren frågar om prenumeration, pris, vad som ingår, abonnemang):
- Prenumerationen kostar 49 kr/månad.
- I prenumerationen ingår tre tjänster: AI Bilrådgivning (hjälp att välja rätt elbil), AI EV Laddassistent (denna chatbot) samt Bränslekostnadsberäkning (jämför elbil mot bensin/diesel).
- Svara med: "Prenumerationen kostar 49 kr/månad och inkluderar tre tjänster: AI Bilrådgivning, AI EV Laddassistent samt Bränslekostnadsberäkning."

BILLIGASTE LADDNING (svara på detta om användaren frågar var de laddar billigast, hur de sparar pengar på laddning etc.):
- Rekommendera ALLTID hemmaladdning som det billigaste alternativet: ~1,50–3,50 kr/kWh beroende på elavtal.
- Om stationskontext finns: nämn också den billigaste publik stationen i listan med namn, pris och avstånd.
- Exempel på svar: "Billigast är att ladda hemma (~1,50–3,50 kr/kWh). Närmaste billiga publika alternativ är [station] ([pris], [avstånd] km bort)."

KONTEXTUELLA FÖLJDFRÅGOR (använd alltid den skickade kontexten för att svara relevant):
- Om kontexten visar "Vald bil: X" — svara alltid utifrån den bilens specs (batteri, DC, räckvidd, pris).
- Om kontexten visar "Laddtidskalkylator" — du vet vad användaren räknat på (från/till%, tid, kostnad, räckvidd). Svara på följdfrågor som "är det snabbt?", "är det dyrt?", "kan jag nå Y med det?".
- Om kontexten visar "Snabbaste DC (topp 3)" eller "Längst räckvidd (topp 3)" — du kan svara på frågor om rankingarna, varför en viss bil är bäst, vad den kostar jämfört med alternativ.
- Om kontexten visar "Faktaruta visad" — du känner till vilket faktum som visades och kan fördjupa det.
- Om kontexten visar "AI-rekommendation" — det är rekommendationen användaren redan sett; du kan förklara/fördjupa den.

RUTTPLANERING (om stationskontexten innehåller en "PLANERAD RUTT"-sektion ska du använda den informationen):
- Om användaren frågar om sin rutt, laddstoppar, om de klarar sträckan eller vilket stopp som är bäst, svara baserat på ruttkontexten.
- Berätta vilket laddningsstopp som rekommenderas och varför (t.ex. strategisk placering halvvägs, snabbast laddning, bäst pris).
- Om kontexten innehåller raden "Bilen klarar sträckan utan laddningsstopp" MÅSTE du inleda svaret med en tydlig, uttrycklig bekräftelse av just detta innan du ger några tips — t.ex. "Din [bilnamn] klarar hela sträckan till [destination] utan laddstopp." Nämn gärna sträckan i km. Ge SEDAN korta råd (t.ex. ladda fullt hemma innan avfärd). Hitta aldrig på ett laddstopp när kontexten säger att inget behövs.
- Om det finns flera stopp, förklara hur de är utplacerade längs rutten.
- Proaktivt tips: om kontexten visar att en rutt är planerad men användaren inte frågat om den, kan du kort nämna "Jag ser att du planerat en rutt till [destination] — vill du veta mer om laddstopparna?"

BUDGET-REGLER (följ dessa exakt):
- Om budgeten är under 200 000 kr: förklara direkt att nya elbilar sällan finns under det priset i Sverige,\s
  och rekommendera begagnade alternativ som Renault Zoe (50–90 tkr), Nissan Leaf (60–120 tkr),\s
  MG ZS EV (80–140 tkr), VW e-Golf (70–110 tkr). Säg aldrig en ny bil som kostar mer än budgeten.
- Om budgeten är 200 000–300 000 kr: visa billigaste nya (Dacia Spring ~180 tkr, MG4 ~260 tkr,\s
  VW ID.3 ~290 tkr) OCH nämn att begagnade kan ge mer för pengarna.
- Rekommendera ALDRIG en bil till mer än 1,3x budgeten utan att tydligt säga att den är över budget.
- Om du föreslår en begagnad bil, säg "begagnad [modell] (~XX–YY tkr)" med prisintervall.

""")
          .append("BILDATA (73 modeller i databasen):\n\n");

        sb.append("Snabbaste DC-laddning:\n");
        cars.stream().filter(c -> c.maxDcKw() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Double.compare(b.maxDcKw(), a.maxDcKw())).limit(5)
            .forEach(c -> sb.append(String.format("  %s: %d kW DC, %d tkr%n",
                c.name(), (int) c.maxDcKw(), c.priceKr() / 1000)));

        sb.append("\nLängst räckvidd (WLTP):\n");
        cars.stream().filter(c -> c.rangeKm() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Integer.compare(b.rangeKm(), a.rangeKm())).limit(5)
            .forEach(c -> sb.append(String.format("  %s: %d km, %d tkr%n",
                c.name(), c.rangeKm(), c.priceKr() / 1000)));

        sb.append("\nBäst värde (km per 100 000 kr):\n");
        cars.stream().filter(c -> c.rangeKm() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Double.compare(
                b.rangeKm() * 100_000.0 / b.priceKr(),
                a.rangeKm() * 100_000.0 / a.priceKr())).limit(5)
            .forEach(c -> sb.append(String.format("  %s: %d km/100tkr, %d tkr%n",
                c.name(), (int)(c.rangeKm() * 100_000.0 / c.priceKr()), c.priceKr() / 1000)));

        sb.append("""

RECENSIONER & UTMÄRKELSER (svenska källor — citera dessa när relevant):

Teknikens Värld:
  Kia EV6: "Bästa elbil under 600 000 kr" – vann årets elbildstest 2022
  Hyundai IONIQ 6: "Räckviddsmästaren" – slog rekord i deras förbrukningstest
  MG4: "Bäst i test budget-elbil 2023" – prisvärd och rymlig
  Tesla Model 3: Konsekvent topplacering, utmärkt driftekonomi
  Volvo EX30: Lyfts fram som "kompakt och prisvärd" för stadskörning
  BMW i4: "Bäst köregenskaper bland elbilar" i körtester
  Polestar 2: "Premiumkänsla till rimligare pris än tyska konkurrenter"

Vi Bilägare:
  Tesla Model Y: "Mest praktiska elbilen för barnfamiljen"
  Kia EV6: Rekommenderas som "bästa köp" i mellanklassen
  Volkswagen ID.3: "Stabilt och tryggt val" för kompaktsegmentet
  Dacia Spring: "Billigast att äga och ladda" i sin klass
  Hyundai IONIQ 5: "Bäst i test" stor elbil – betyg 5/5 interiör

Råd & Rön:
  Tesla Model 3: Rekommenderas för lägst driftkostnad per mil
  MG4: Prisvärd med bra ägarbetyg från svenska ägare

Instruktion: När du rekommenderar en bil, nämn kort om den fått bra recensioner från dessa källor.
Säg alltid vilken källa du refererar till (t.ex. "Enligt Teknikens Värld...").
Hitta INTE på recensioner som inte finns i listan ovan.
""");

        return sb.toString();
    }

    public InputStream chatStream(List<Map<String, String>> history, List<CarSpec> cars, String stationContext) throws Exception {
        if (isQuotaExceeded()) throw new RuntimeException("AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!");
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        String sysPrompt = buildChatSystemPrompt(cars);
        if (stationContext != null && !stationContext.isBlank())
            sysPrompt += "\n\nAktuella laddstationer i sökningen:\n" + stationContext;
        messages.add(Map.of("role", "system", "content", sysPrompt));
        List<Map<String, String>> trimmedStream = history.size() > CHAT_MAX_HISTORY
                ? history.subList(history.size() - CHAT_MAX_HISTORY, history.size()) : history;
        trimmedStream.forEach(m -> messages.add(Map.of("role", (Object) m.get("role"), "content", (Object) m.get("content"))));

        Map<String, Object> body = Map.of(
                "model", MODEL, "max_tokens", 800, "temperature", 0.7, "stream", true,
                "reasoning_effort", "low",
                "messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(groqUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 401) throw new RuntimeException("AI-tjänsten är inte korrekt konfigurerad.");
        if (response.statusCode() == 429) {
            String body429 = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(body429);
            throw new RuntimeException("AI-assistenten är tillfälligt otillgänglig — dagsgränsen är nådd. Försök igen imorgon!");
        }
        if (response.statusCode() != 200) throw new RuntimeException("AI-tjänsten svarade med fel " + response.statusCode() + ".");
        return response.body();
    }

    public GroqResult recommend(CarSpec car, List<StationDto> stations, String costComparison) {
        String key = car.name() + stations.stream().limit(3).map(StationDto::name).reduce("", (a, b) -> a + "|" + b);
        CacheEntry hit = recommendCache.get(key);
        if (hit != null && System.currentTimeMillis() - hit.timestamp() < CACHE_TTL) return hit.result();

        if (System.currentTimeMillis() < quotaExceededUntil) return buildFallback(car, stations);

        String userPrompt = buildPrompt(car, stations, costComparison);
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 800,
                "temperature", 0.8,
                "reasoning_effort", "low",
                "messages", List.of(
                        Map.of("role", "system", "content",
                                "Du är en expert på elbilsladdning i Sverige. " +
                                "Svara alltid på svenska. Svara i exakt detta format:\n" +
                                "REKOMMENDATION: [2–3 meningar med konkret råd om vilken station som passar bäst. Hitta aldrig på priser.]\n" +
                                "VISSTE DU ATT: [ett kort, intressant faktum om bilen eller elbilsladdning i allmänhet. Max 1 mening.]"),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post()
                    .uri(groqUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            @SuppressWarnings("unchecked")
            var choices = (List<Map<String, Object>>) resp.get("choices");
            var message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            String recommendation = extractSectionCI(content, "REKOMMENDATION:", "VISSTE DU ATT:");
            String funFact        = extractSectionCI(content, "VISSTE DU ATT:", null);
            quotaExceededUntil = 0;
            GroqResult result = new GroqResult(recommendation, funFact);
            if (recommendCache.size() > 200) recommendCache.clear();
            recommendCache.put(key, new CacheEntry(result, System.currentTimeMillis()));
            return result;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(e.getResponseBodyAsString());
            }
            GroqResult fallback = buildFallback(car, stations);
            recommendCache.put(key, new CacheEntry(fallback, System.currentTimeMillis()));
            return fallback;
        } catch (Exception e) {
            return buildFallback(car, stations);
        }
    }

    GroqResult buildFallback(CarSpec car, List<StationDto> stations) {
        if (stations.isEmpty()) return new GroqResult("Inga laddstationer hittades i närheten.", null);
        StationDto best = stations.get(0);
        String rec = String.format("%s (%.1f km, %d kW %s) passar bäst för din %s.",
                best.name(), best.distanceKm(), (int) best.maxEffKw(), best.connectorType(), car.name());
        return new GroqResult(rec, null);
    }

    long parseRetryMs(String body) {
        try {
            Matcher m = Pattern.compile("try again in ([\\d]+m[\\d.]+s|[\\d.]+s)").matcher(body);
            if (!m.find()) return 15 * 60 * 1000L;
            String t = m.group(1);
            Matcher min = Pattern.compile("(\\d+)m").matcher(t);
            Matcher sec = Pattern.compile("([\\d.]+)s").matcher(t);
            int minutes = min.find() ? Integer.parseInt(min.group(1)) : 0;
            double seconds = sec.find() ? Double.parseDouble(sec.group(1)) : 0;
            return (long) ((minutes * 60 + seconds) * 1000);
        } catch (Exception e) {
            return 15 * 60 * 1000L;
        }
    }

    /** Case-insensitive section extraction — handles AI variations like "Visste du att:" vs "VISSTE DU ATT:". */
    String extractSectionCI(String text, String startMarker, String endMarker) {
        String upper = text.toUpperCase();
        int start = upper.indexOf(startMarker.toUpperCase());
        if (start < 0) return null;
        start += startMarker.length();
        int end = endMarker != null
                ? upper.indexOf(endMarker.toUpperCase(), start)
                : text.length();
        if (end < 0) end = text.length();
        return text.substring(start, end).strip();
    }

    String buildPrompt(CarSpec car, List<StationDto> stations, String costComparison) {
        var sb = new StringBuilder();
        sb.append("Bil: ").append(car.name())
          .append(" (DC max ").append((int) car.maxDcKw())
          .append(" kW, AC max ").append((int) car.maxAcKw()).append(" kW)\n\n");
        sb.append("Topp laddstationer:\n");

        int n = Math.min(5, stations.size());
        for (int i = 0; i < n; i++) {
            StationDto s = stations.get(i);
            String bp   = s.bestPrice();
            String pris = bp != null ? "Pris: " + bp : "Pris: okänt";
            sb.append(i + 1).append(". ").append(s.name())
              .append(" – ").append(String.format("%.1f", s.distanceKm())).append(" km")
              .append(" – ").append((int) s.maxEffKw()).append(" kW effektivt")
              .append(" (").append(s.connectorType()).append(")")
              .append(" – ").append(pris)
              .append("\n");
        }

        if (costComparison != null) {
            sb.append("\nKostnadsjämförelse med andra elbilar:\n").append(costComparison).append("\n");
        }

        sb.append("\nVilken station rekommenderar du och varför? ")
          .append("Om priset är okänt, nämn det och be användaren kolla operatörens app.");
        return sb.toString();
    }
}
