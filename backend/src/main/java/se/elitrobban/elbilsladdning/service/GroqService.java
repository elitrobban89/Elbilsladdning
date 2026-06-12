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

    public GroqResult recommend(CarSpec car, List<StationDto> stations, String costComparison) {
        String userPrompt = buildPrompt(car, stations, costComparison);

        Map<String, Object> body = Map.of(
                "model", MODEL,
                "max_tokens", 350,
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

            String recommendation = extractSection(content, "REKOMMENDATION:", "VISSTE DU ATT:");
            String funFact        = extractSection(content, "VISSTE DU ATT:", null);
            return new GroqResult(recommendation, funFact);

        } catch (Exception e) {
            return new GroqResult(null, null);
        }
    }

    private String extractSection(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) return null;
        start += startMarker.length();
        int end = endMarker != null ? text.indexOf(endMarker, start) : text.length();
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
