package se.elitrobban.elbilsladdning.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.elitrobban.elbilsladdning.model.FavoriteStation;
import se.elitrobban.elbilsladdning.repository.FavoriteStationRepository;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteStationRepository repo;

    public FavoriteController(FavoriteStationRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{userId}")
    public List<FavoriteStation> getFavorites(@PathVariable String userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @PostMapping
    public ResponseEntity<FavoriteStation> addFavorite(@RequestBody FavoriteStation station) {
        if (repo.existsByUserIdAndNameAndLat(station.getUserId(), station.getName(), station.getLat())) {
            return ResponseEntity.status(409).build();
        }
        return ResponseEntity.ok(repo.save(station));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long id, @RequestParam String userId) {
        var found = repo.findById(id).filter(f -> f.getUserId().equals(userId));
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        repo.delete(found.get());
        return ResponseEntity.<Void>noContent().build();
    }
}
