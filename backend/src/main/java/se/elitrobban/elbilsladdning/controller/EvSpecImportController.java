package se.elitrobban.elbilsladdning.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Import av elbilsspecar till {@code ev_spec}.
 *
 * <p>Tabellen fylls inte av någon synk här — den har inget seedsteg och inget schemalagt jobb,
 * och därför halkade den efter systerprojektets. Mätt 2026-08-28: CarAdvice hade 520 EV-rader,
 * den här tjänsten 462. De 58 som saknades var inte skräp utan bilar folk faktiskt kör —
 * Hyundai Ioniq 5, Kia EV9, Ford Mustang Mach-E, BMW iX3, Cupra Born, Volvo EX60 och EX40 —
 * och en laddassistent som inte känner igen bilen kan inte svara på någonting alls.
 *
 * <p>Skriver med {@link JdbcTemplate} och inte via JPA med flit: {@code EvSpecEntity} är
 * {@code @Immutable} och saknar settrar, alltså är läsmodellen medvetet skrivskyddad. Ett
 * uttryckligt INSERT här är ärligare än att luckra upp entiteten för importens skull.
 *
 * <p><b>Idempotent:</b> en rad vars {@code car_name} redan finns hoppas över i stället för att
 * uppdateras. Importen ska kunna köras om utan att skriva över en rad någon rättat för hand,
 * och svaret räknar upp både det som lades in och det som hoppades över.
 */
@RestController
@RequestMapping("/api")
public class EvSpecImportController {

    private static final Logger log = LoggerFactory.getLogger(EvSpecImportController.class);

    @Value("${admin.key}")
    private String adminKey;

    private final JdbcTemplate jdbc;

    public EvSpecImportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Rå läsvy över {@code ev_spec} — namn och {@code car_type}, inget annat.
     *
     * <p>Byggd 2026-08-28 efter att importen av 58 "saknade" modeller svarade
     * {@code fannsRedan: 58}. Raderna fanns alltså, men syntes inte i {@code /api/cars}, som
     * bara läser {@code car_type = 'EV'}. Utan den här vyn går det inte att skilja "raden
     * saknas" från "raden har fel typ", och att gissa mellan dem hade betytt en INSERT som
     * skapat dubbletter av rader som redan låg där.
     */
    @GetMapping("/admin/ev-specs")
    public ResponseEntity<?> listEvSpecs(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (isAdminUnauthorized(key)) return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        List<Map<String, Object>> rader = jdbc.queryForList(
                "SELECT car_name, car_type, battery_kwh, range_km, max_dc_kw FROM ev_spec ORDER BY car_name");
        Map<String, Integer> perTyp = new java.util.LinkedHashMap<>();
        for (Map<String, Object> r : rader) {
            String t = r.get("car_type") == null ? "(null)" : String.valueOf(r.get("car_type"));
            perTyp.merge(t, 1, Integer::sum);
        }
        return ResponseEntity.ok(Map.of("total", rader.size(), "perTyp", perTyp, "rader", rader));
    }

    @PostMapping("/admin/import/ev-specs")
    public ResponseEntity<?> importEvSpecs(@RequestHeader(value = "X-Admin-Key", required = false) String key,
                                           @RequestBody List<Map<String, Object>> rader) {
        if (isAdminUnauthorized(key)) return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        if (rader == null || rader.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "tom lista"));

        List<String> nya = new ArrayList<>();
        List<String> fanns = new ArrayList<>();
        List<String> utanNamn = new ArrayList<>();

        for (Map<String, Object> r : rader) {
            String namn = text(r.get("carName"));
            if (namn == null || namn.isBlank()) { utanNamn.add(String.valueOf(r)); continue; }
            Integer antal = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ev_spec WHERE car_name = ?", Integer.class, namn);
            if (antal != null && antal > 0) { fanns.add(namn); continue; }
            jdbc.update("INSERT INTO ev_spec (car_name, car_type, battery_kwh, range_km,"
                            + " max_dc_kw, max_ac_kw, price_kr) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    namn, text(r.get("carType")) == null ? "EV" : text(r.get("carType")),
                    tal(r.get("batteryKwh")), heltal(r.get("rangeKm")),
                    tal(r.get("maxDcKw")), tal(r.get("maxAcKw")), heltal(r.get("priceKr")));
            nya.add(namn);
        }
        log.info("ev_spec-import: {} nya, {} fanns redan, {} utan namn", nya.size(), fanns.size(), utanNamn.size());
        return ResponseEntity.ok(Map.of(
                "nya", nya.size(), "fannsRedan", fanns.size(), "utanNamn", utanNamn.size(),
                "namn", nya));
    }

    private static String text(Object o) { return o == null ? null : String.valueOf(o); }

    private static Double tal(Object o) {
        if (o == null) return null;
        try { return Double.valueOf(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }

    private static Integer heltal(Object o) {
        Double d = tal(o);
        return d == null ? null : (int) Math.round(d);
    }

    private boolean isAdminUnauthorized(String key) {
        return key == null || adminKey == null || adminKey.isBlank() || !adminKey.equals(key);
    }
}
