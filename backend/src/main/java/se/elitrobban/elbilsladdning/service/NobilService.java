package se.elitrobban.elbilsladdning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

@Service
public class NobilService {

    private static final String NOBIL_URL = "https://nobil.no/api/server/search.php";

    @Value("${nobil.api.key:}")
    private String apiKey;

    private final RestClient http = RestClient.create();

    public record NobilStation(double lat, double lon, int connectorCount, boolean offline) {}

    public boolean isEnabled() { return apiKey != null && !apiKey.isBlank(); }

    public List<NobilStation> getStations(double lat, double lon) {
        if (!isEnabled()) return List.of();
        try {
            String url = NOBIL_URL
                    + "?apikey=" + apiKey
                    + "&apiVersion=3&action=search&type=nearbypoint"
                    + "&lat=" + lat + "&lon=" + lon
                    + "&limit=60&distance=25&format=json";

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (resp == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stations = (List<Map<String, Object>>) resp.get("chargerstations");
            if (stations == null) return List.of();

            List<NobilStation> result = new ArrayList<>();
            for (Map<String, Object> st : stations) {
                NobilStation ns = parse(st);
                if (ns != null) result.add(ns);
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private NobilStation parse(Map<String, Object> st) {
        try {
            var csmd = (Map<String, Object>) st.get("csmd");
            if (csmd == null) return null;

            String pos = (String) csmd.get("Position");
            if (pos == null) return null;
            String[] parts = pos.split(",");
            if (parts.length < 2) return null;
            double stLat = Double.parseDouble(parts[0].trim());
            double stLon = Double.parseDouble(parts[1].trim());

            boolean offline = false;
            int connectorCount = 0;

            var attr = (Map<String, Object>) st.get("attr");
            if (attr != null) {
                var stAttr = (Map<String, Object>) attr.get("st");
                if (stAttr != null) {
                    var st1 = (Map<String, Object>) stAttr.get("1");
                    if (st1 != null) {
                        String status = (String) st1.get("attrval");
                        if (status != null) {
                            String sl = status.toLowerCase();
                            offline = sl.contains("nedlagt") || sl.contains("decommission")
                                   || sl.contains("ikke i drift") || sl.contains("ej i drift")
                                   || sl.contains("planlagt");
                        }
                    }
                }

                var connAttr = (List<Map<String, Object>>) attr.get("conn");
                if (connAttr != null) {
                    for (Map<String, Object> conn : connAttr) {
                        // attribute 4 = number of charging points for this connector group
                        var a4 = (Map<String, Object>) conn.get("4");
                        if (a4 != null) {
                            Object val = a4.get("attrval");
                            if (val != null) {
                                try { connectorCount += Integer.parseInt(val.toString().trim()); }
                                catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }

            return new NobilStation(stLat, stLon, connectorCount, offline);
        } catch (Exception e) {
            return null;
        }
    }

    /** Haversine distance in km. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                 + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon/2)*Math.sin(dLon/2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    }
}
