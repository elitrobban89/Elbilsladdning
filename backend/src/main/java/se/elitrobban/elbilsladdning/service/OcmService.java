package se.elitrobban.elbilsladdning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.util.*;

@Service
public class OcmService {

    private static final String OCM_URL = "https://api.openchargemap.io/v3/poi/";
    private static final Map<String, int[]> OCM_IDS = Map.of(
            "type2",   new int[]{25, 1036},
            "ccs",     new int[]{33},
            "chademo", new int[]{2}
    );

    @Value("${ocm.api.key}")
    private String apiKey;

    private final RestClient http = RestClient.create();

    public List<StationDto> findNearby(double lat, double lon, CarSpec car) {
        String url = OCM_URL +
                "?output=json&latitude=" + lat + "&longitude=" + lon +
                "&distance=25&distanceunit=KM&maxresults=30" +
                "&compact=false&verbose=false&key=" + apiKey;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> raw = http.get()
                .uri(url)
                .retrieve()
                .body(List.class);

        if (raw == null) return List.of();

        Set<Integer> allowed = new HashSet<>();
        for (String t : car.connectors())
            for (int id : OCM_IDS.getOrDefault(t, new int[]{}))
                allowed.add(id);

        List<StationDto> result = new ArrayList<>();
        for (Object item : raw) {
            @SuppressWarnings("unchecked")
            var s = (Map<String, Object>) item;
            StationDto dto = parse(s, car, allowed);
            if (dto != null) result.add(dto);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private StationDto parse(Map<String, Object> s, CarSpec car, Set<Integer> allowed) {
        try {
            var addr = (Map<String, Object>) s.get("AddressInfo");
            if (addr == null) return null;

            String name    = str(addr, "Title", "Okänd");
            String street  = str(addr, "AddressLine1", "");
            String town    = str(addr, "Town", "");
            String address = (street + (town.isBlank() ? "" : ", " + town)).strip();
            double dist    = dbl(addr, "Distance");
            double stLat   = dbl(addr, "Latitude");
            double stLon   = dbl(addr, "Longitude");
            String cost    = str(s, "UsageCost", "");
            var    opInfo  = (Map<String, Object>) s.get("OperatorInfo");
            String op      = opInfo != null ? str(opInfo, "Title", "") : "";

            var connsRaw = (List<Object>) s.get("Connections");
            if (connsRaw == null) return null;

            double maxEffKw = 0, bestStationKw = 0;
            String bestType = "";

            for (Object connObj : connsRaw) {
                var conn   = (Map<String, Object>) connObj;
                int typeId = (int) lng(conn, "ConnectionTypeID");
                double kw  = dbl(conn, "PowerKW");
                if (!allowed.contains(typeId) || kw <= 0) continue;

                String ctype = OCM_IDS.entrySet().stream()
                        .filter(e -> { for (int id : e.getValue()) if (id == typeId) return true; return false; })
                        .map(Map.Entry::getKey).findFirst().orElse("?");

                double effKw = Math.min(kw, car.maxKwForType(ctype));
                if (effKw > maxEffKw) { maxEffKw = effKw; bestStationKw = kw; bestType = ctype; }
            }

            if (maxEffKw == 0) return null;

            String connLabel = switch (bestType) {
                case "type2"   -> "Type 2 (AC)";
                case "ccs"     -> "CCS Combo 2 (DC)";
                case "chademo" -> "CHAdeMO (DC)";
                default        -> bestType;
            };

            return new StationDto(name, address, dist, stLat, stLon, maxEffKw, bestStationKw, connLabel, op, cost, null, 0);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k); return v instanceof String s ? s : def;
    }
    private double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Double d) return d;
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l;
        return 0.0;
    }
    private long lng(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l;
        if (v instanceof Double d) return d.longValue();
        return -1L;
    }
}
