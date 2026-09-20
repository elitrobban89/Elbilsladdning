package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.elitrobban.elbilsladdning.model.FavoriteStation;

import java.util.List;

/** @author Robert Andersson Kopler */
public interface FavoriteStationRepository extends JpaRepository<FavoriteStation, Long> {
    List<FavoriteStation> findByUserIdOrderByCreatedAtDesc(String userId);
    boolean existsByUserIdAndNameAndLat(String userId, String name, double lat);
}
