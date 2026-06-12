package se.elitrobban.elbilsladdning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChargepriceService {

    private static final String CP_URL = "https://api.chargeprice.app/v1/charge_prices";

    // Maps OCM connector display name → Chargeprice plug key
    private static final Map<String, String> PLUG_MAP = Map.of(
            "Type 2 (AC)",       "type2",
            "CCS Combo 2 (DC)",  "ccs",
            "CHAdeMO (DC)",      "chademo"
    );

    @Value("${chargeprice.api.key:}")
    private String apiKey;

    private final RestClient http = RestClient.create();

    // Cache by (operator + power + plug) to avoid repeated calls for same setup
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    /**
     * Returns a formatted price string (e.g. "0.69 €/kWh") or null if unavailable.
     */
    public String getPricePerKwh(StationDto station, CarSpec car) {
        if (!isEnabled()) return null;

        String plug = PLUG_MAP.getOrDefault(station.connectorType(), "ccs");
        String cacheKey = station.operator() + "_" + (int) station.stationKw() + "_" + plug;

        return cache.computeIfAbsent(cacheKey, k -> Optional.ofNullable(fetch(station, car, plug)))
                    .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private String fetch(StationDto station, CarSpec car, String plug) {
        try {
            String network = normalizeNetwork(station.operator());

            Map<String, Object> body = Map.of(
                "data", Map.of(
                    "type", "charge_price_request",
                    "attributes", Map.of(
                        "data_adapter", "going_electric",
                        "station", Map.of(
                            "network",       network,
                            "charge_points", List.of(Map.of("power", (int) station.stationKw(), "plug", plug))
                        ),
                        "vehicle", Map.of(
                            "battery_range",     List.of(20, 80),
                            "ac_charging_power", (int) car.maxAcKw(),
                            "dc_charging_power", (int) car.maxDcKw()
                        ),
                        "options", Map.of(
                            "provider_customer_tariffs", false,
                            "max_monthly_fees",          0,
                            "allow_unbalanced_load",     true
                        )
                    )
                )
            );

            Map<String, Object> resp = http.post()
                    .uri(CP_URL)
                    .header("Api-Key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) return null;

            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
            if (data == null || data.isEmpty()) return null;

            // Pick tariff with lowest per-kWh price from price_distribution
            return data.stream()
                    .map(d -> (Map<String, Object>) d.get("attributes"))
                    .filter(Objects::nonNull)
                    .min(Comparator.comparingDouble(a -> {
                        Map<?, ?> dist = (Map<?, ?>) a.get("price_distribution");
                        return dist != null ? toDouble(dist.get("per_kwh")) : Double.MAX_VALUE;
                    }))
                    .map(a -> {
                        Map<?, ?> dist     = (Map<?, ?>) a.get("price_distribution");
                        String currency    = (String) a.getOrDefault("currency", "EUR");
                        double perKwh      = dist != null ? toDouble(dist.get("per_kwh")) : 0;
                        double totalPrice  = toDouble(a.get("price"));
                        if (perKwh > 0)    return String.format("%.2f %s/kWh", perKwh, currency);
                        if (totalPrice > 0) return String.format("%.2f %s", totalPrice, currency);
                        return null;
                    })
                    .orElse(null);

        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeNetwork(String operator) {
        if (operator == null || operator.isBlank()) return "Unknown";
        // Common Swedish operator mappings to Chargeprice network names
        String lower = operator.toLowerCase();
        if (lower.contains("ionity"))    return "IONITY";
        if (lower.contains("tesla"))     return "Tesla";
        if (lower.contains("recharge"))  return "Recharge";
        if (lower.contains("incharge") || lower.contains("vattenfall")) return "InCharge";
        if (lower.contains("circle k") || lower.contains("shell"))      return "Circle K";
        if (lower.contains("bee"))       return "Bee Charging";
        if (lower.contains("e.on") || lower.contains("eon"))            return "E.ON Drive";
        if (lower.contains("clever"))    return "Clever";
        // Fall back to the original name and hope Chargeprice recognises it
        return operator;
    }

    private double toDouble(Object v) {
        if (v instanceof Double d)  return d;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l)    return l;
        return 0.0;
    }
}
