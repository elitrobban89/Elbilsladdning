package se.elitrobban.elbilsladdning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    = "llama-3.3-70b-versatile";

    @Value("${groq.api.key}")
    private String apiKey;

    private final RestClient http = RestClient.create();

    public record GroqResult(String recommendation, String funFact) {}

    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> history, List<CarSpec> cars) {
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildChatSystemPrompt(cars)));
        history.forEach(m -> messages.add(Map.of("role", (Object) m.get("role"), "content", (Object) m.get("content"))));

        Map<String, Object> body = Map.of(
                "model", MODEL, "max_tokens", 350, "temperature", 0.7,
                "messages", messages);
        try {
            Map<String, Object> resp = http.post()
                    .uri(GROQ_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            var choices = (List<Map<String, Object>>) resp.get("choices");
            var msg     = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.get("content");
        } catch (Exception e) {
            return "Tyvärr kunde jag inte svara just nu. Försök igen!";
        }
    }

    private String buildChatSystemPrompt(List<CarSpec> cars) {
        var sb = new StringBuilder();
        sb.append("""
Du är en assistent för en EV-laddningsapp i Sverige.
Du svarar ENDAST på frågor om elbilar, laddning, räckvidd, laddstationer och bilresor.

Om användaren frågar om något annat svarar du:
"Det kan jag inte hjälpa med — jag är specialiserad på elbilar och laddning."

Svara alltid på svenska. Svara kortfattat (max 3–4 meningar) men konkret och specifik med siffror.
Om någon frågar om elbilsköp och inte angett budget, fråga efter det.

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
            .sorted((a, b) -> Double.compare(b.maxDcKw(), a.maxDcKw())).limit(10)
            .forEach(c -> sb.append(String.format("  %s: %d kW DC, %d tkr%n",
                c.name(), (int) c.maxDcKw(), c.priceKr() / 1000)));

        sb.append("\nLängst räckvidd (WLTP):\n");
        cars.stream().filter(c -> c.rangeKm() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Integer.compare(b.rangeKm(), a.rangeKm())).limit(10)
            .forEach(c -> sb.append(String.format("  %s: %d km, %d tkr%n",
                c.name(), c.rangeKm(), c.priceKr() / 1000)));

        sb.append("\nBäst värde (km per 100 000 kr):\n");
        cars.stream().filter(c -> c.rangeKm() > 0 && c.priceKr() > 0)
            .sorted((a, b) -> Double.compare(
                b.rangeKm() * 100_000.0 / b.priceKr(),
                a.rangeKm() * 100_000.0 / a.priceKr())).limit(10)
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

    public GroqResult recommend(CarSpec car, List<StationDto> stations, String costComparison) {
        String userPrompt = buildPrompt(car, stations, costComparison);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 450,
                "temperature", 0.8,
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
                    .uri(GROQ_URL)
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
            return new GroqResult(recommendation, funFact);

        } catch (Exception e) {
            return new GroqResult(null, null);
        }
    }

    /** Case-insensitive section extraction — handles AI variations like "Visste du att:" vs "VISSTE DU ATT:". */
    private String extractSectionCI(String text, String startMarker, String endMarker) {
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

    private String buildPrompt(CarSpec car, List<StationDto> stations, String costComparison) {
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
