package se.elitrobban.elbilsladdning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ApiNinjasService {

    private static final String BASE_URL = "https://api.api-ninjas.com/v1/evcharger";

    @Value("${apininjas.api.key:}")
    private String apiKey;

    private final RestClient http = RestClient.create();

    // Cache per city to avoid repeated requests
    private final Map<String, List<NinjaStation>> cache = new ConcurrentHashMap<>();

    public boolean isEnabled() { return !apiKey.isBlank(); }

    record NinjaStation(
            String name,
            String network,
            double lat,
            double lon,
            String evPricing
    ) {}

    /**
     * Returns the best pricing string for an OCM station, matched by proximity.
     * Returns null if no match or API is disabled.
     */
    public String getPricing(String city, double stationLat, double stationLon) {
        if (!isEnabled() || city == null || city.isBlank()) return null;

        List<NinjaStation> nearby = cache.computeIfAbsent(city, this::fetch);

        // Find the closest API Ninjas station within 300m
        return nearby.stream()
                .filter(n -> n.evPricing() != null && !n.evPricing().isBlank()
                          && !n.evPricing().equalsIgnoreCase("Varies"))
                .filter(n -> distanceMeters(stationLat, stationLon, n.lat(), n.lon()) < 300)
                .min((a, b) -> Double.compare(
                        distanceMeters(stationLat, stationLon, a.lat(), a.lon()),
                        distanceMeters(stationLat, stationLon, b.lat(), b.lon())))
                .map(NinjaStation::evPricing)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<NinjaStation> fetch(String city) {
        try {
            String url = BASE_URL + "?city=" +
                    java.net.URLEncoder.encode(city, java.nio.charset.StandardCharsets.UTF_8) +
                    "&country=SE&min_kw=11";

            List<Map<String, Object>> raw = http.get()
                    .uri(url)
                    .header("X-Api-Key", apiKey)
                    .retrieve()
                    .body(List.class);

            if (raw == null) return List.of();

            List<NinjaStation> result = new ArrayList<>();
            for (Object item : raw) {
                var s = (Map<String, Object>) item;
                double lat = dbl(s, "latitude");
                double lon = dbl(s, "longitude");
                if (lat == 0 && lon == 0) continue;

                String pricing = str(s, "ev_pricing");
                if (pricing == null || pricing.isBlank()) continue;

                result.add(new NinjaStation(
                        str(s, "station_name"),
                        str(s, "ev_network"),
                        lat, lon,
                        pricing
                ));
            }
            return result;

        } catch (Exception e) {
            return List.of();
        }
    }

    /** Haversine distance in metres between two lat/lon points. */
    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k); return v instanceof String s ? s : null;
    }

    private double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Double d)  return d;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l)    return l;
        return 0.0;
    }
}
