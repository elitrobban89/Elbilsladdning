package se.elitrobban.elbilsladdning.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.elitrobban.elbilsladdning.model.EvValueRetentionEntry;
import se.elitrobban.elbilsladdning.repository.EvValueRetentionRepository;
import se.elitrobban.elbilsladdning.scraper.EvValueRetentionSyncService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EvValueRetentionController {

    @Value("${admin.key}")
    private String adminKey;

    private final EvValueRetentionRepository repo;
    private final EvValueRetentionSyncService syncService;

    public EvValueRetentionController(EvValueRetentionRepository repo,
                                      EvValueRetentionSyncService syncService) {
        this.repo = repo;
        this.syncService = syncService;
    }

    /**
     * Publik: elbilar sorterade med störst värdetapp först.
     *
     * <p><b>Källan följer med i svaret</b> och inte bara i koden. Nypriserna kommer från Kvdbil
     * och Bilpriser medan medianen är vår egen mätning på Blocket — två källor i samma tal, och
     * då måste frontenden kunna skriva ut vem som står för vad utan att hårdkoda det.
     *
     * <p>Tom lista före första lyckade synk, precis som ev-sales-rank.
     */
    @GetMapping("/value-retention")
    public Map<String, Object> valueRetention() {
        Map<String, Object> svar = new LinkedHashMap<>();
        svar.put("modeller", repo.findAllByOrderByRetentionPctAsc());
        svar.put("nyprisKalla", EvValueRetentionSyncService.NYPRIS_KALLA);
        svar.put("prisKalla", "Medianpris bland dagens annonser på Blocket, max 15 000 mil");
        return svar;
    }

    /** Admin: kör synken nu. Tar ~10 s (tio Blocket-anrop), så svaret visar utfallet direkt. */
    @PostMapping("/admin/sync-value-retention")
    public ResponseEntity<?> syncValueRetention(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (isAdminUnauthorized(key)) return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
        Map<String, Object> result = syncService.syncNow();
        return "OK".equals(result.get("status"))
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(502).body(result);
    }

    private boolean isAdminUnauthorized(String key) {
        return key == null || adminKey == null || adminKey.isBlank() || !adminKey.equals(key);
    }
}
