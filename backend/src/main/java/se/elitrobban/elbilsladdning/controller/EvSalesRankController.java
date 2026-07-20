package se.elitrobban.elbilsladdning.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.elitrobban.elbilsladdning.model.EvSalesRankEntry;
import se.elitrobban.elbilsladdning.repository.EvSalesRankRepository;
import se.elitrobban.elbilsladdning.scraper.EvSalesRankSyncService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EvSalesRankController {

    @Value("${admin.key}")
    private String adminKey;

    private final EvSalesRankRepository repo;
    private final EvSalesRankSyncService syncService;

    public EvSalesRankController(EvSalesRankRepository repo, EvSalesRankSyncService syncService) {
        this.repo = repo;
        this.syncService = syncService;
    }

    /** Publik: senaste kända ranking (tom lista innan första lyckade synk). */
    @GetMapping("/ev-sales-rank")
    public List<EvSalesRankEntry> evSalesRank() {
        return repo.findAllByOrderByRankAsc();
    }

    // Admin: hämta senaste elbilsvaruhuset.se-inlägget och ersätt rankingtabellen.
    // Körs synkront (~1 s) så svaret visar resultatet direkt.
    @PostMapping("/admin/sync-ev-sales-rank")
    public ResponseEntity<?> syncEvSalesRank(@RequestHeader(value = "X-Admin-Key", required = false) String key) {
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
