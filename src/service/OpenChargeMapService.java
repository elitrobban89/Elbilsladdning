package service;

import config.Config;
import model.*;
import util.SimpleJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class OpenChargeMapService {

    private static final String OCM_URL = "https://api.openchargemap.io/v3/poi/";
    private final HttpClient client = HttpClient.newHttpClient();

    public List<ChargingStation> findNearby(double lat, double lon, CarModel car) throws Exception {
        String url = OCM_URL +
                "?output=json" +
                "&latitude=" + lat +
                "&longitude=" + lon +
                "&distance=" + Config.SEARCH_RADIUS_KM +
                "&distanceunit=KM" +
                "&maxresults=" + Config.MAX_RESULTS +
                "&compact=false" +
                "&verbose=false" +
                (Config.OCM_API_KEY.isBlank() ? "" : "&key=" + Config.OCM_API_KEY);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "EV-Laddning-App/1.0")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200)
            throw new RuntimeException("OCM API HTTP " + resp.statusCode());

        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) SimpleJson.parse(resp.body());

        List<ChargingStation> stations = new ArrayList<>();
        for (Object item : items) {
            @SuppressWarnings("unchecked")
            var s = (Map<String, Object>) item;
            ChargingStation cs = parseStation(s, car);
            if (cs != null && !cs.connections().isEmpty())
                stations.add(cs);
        }
        return stations;
    }

    private ChargingStation parseStation(Map<String, Object> s, CarModel car) {
        try {
            @SuppressWarnings("unchecked")
            var addr = (Map<String, Object>) s.get("AddressInfo");
            if (addr == null) return null;

            String name     = str(addr, "Title", "Okänd station");
            String street   = str(addr, "AddressLine1", "");
            String town     = str(addr, "Town", "");
            String address  = (street + (town.isBlank() ? "" : ", " + town)).strip();
            double distance = dbl(addr, "Distance", 0.0);
            String cost     = str(s, "UsageCost", "");

            @SuppressWarnings("unchecked")
            var op = (Map<String, Object>) s.get("OperatorInfo");
            String operator = op != null ? str(op, "Title", "") : "";

            @SuppressWarnings("unchecked")
            var connsJson = (List<Object>) s.get("Connections");

            List<ConnectionPoint> connections = new ArrayList<>();
            if (connsJson != null) {
                for (Object connObj : connsJson) {
                    @SuppressWarnings("unchecked")
                    var conn = (Map<String, Object>) connObj;
                    int typeId = (int) lng(conn, "ConnectionTypeID", -1);
                    double power = dbl(conn, "PowerKW", 0.0);
                    int qty = (int) lng(conn, "Quantity", 1);

                    ConnectorType ct = ConnectorType.fromOcmId(typeId);
                    if (ct != null && car.connectors().contains(ct) && power > 0)
                        connections.add(new ConnectionPoint(ct, power, qty));
                }
            }

            return new ChargingStation(name, address, distance, connections, cost, operator);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v instanceof String s ? s : def;
    }

    private double dbl(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        if (v instanceof Double d) return d;
        if (v instanceof Long   l) return (double) l;
        return def;
    }

    private long lng(Map<String, Object> m, String k, long def) {
        Object v = m.get(k);
        if (v instanceof Long   l) return l;
        if (v instanceof Double d) return d.longValue();
        return def;
    }
}
