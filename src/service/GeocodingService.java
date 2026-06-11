package service;

import util.SimpleJson;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class GeocodingService {

    private static final String NOMINATIM  = "https://nominatim.openstreetmap.org/search";
    private static final String IP_API     = "http://ip-api.com/json/";
    private final HttpClient client = HttpClient.newHttpClient();

    /** Returnerar [lat, lon, visningsnamn] via IP-geolokalisering. */
    public Object[] locateByIp() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(IP_API + "?fields=status,city,lat,lon"))
                .header("User-Agent", "EV-Laddning-App/1.0")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) SimpleJson.parse(resp.body());

        if (!"success".equals(body.get("status")))
            throw new RuntimeException("IP-lokalisering misslyckades");

        double lat  = dbl(body, "lat");
        double lon  = dbl(body, "lon");
        String city = (String) body.get("city");
        return new Object[]{lat, lon, city};
    }

    /** Returnerar [lat, lon] från fritext-adress via Nominatim. */
    public double[] geocode(String query) throws Exception {
        String url = NOMINATIM + "?q=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8) +
                "&format=json&limit=1";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "EV-Laddning-App/1.0 (educational)")
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200)
            throw new RuntimeException("Nominatim HTTP " + resp.statusCode());

        @SuppressWarnings("unchecked")
        List<Object> results = (List<Object>) SimpleJson.parse(resp.body());

        if (results.isEmpty())
            throw new RuntimeException("Platsen hittades inte: \"" + query + "\"");

        @SuppressWarnings("unchecked")
        Map<String, Object> hit = (Map<String, Object>) results.get(0);

        return new double[]{
            Double.parseDouble((String) hit.get("lat")),
            Double.parseDouble((String) hit.get("lon"))
        };
    }

    private double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Double d) return d;
        if (v instanceof Long   l) return (double) l;
        return 0.0;
    }
}
