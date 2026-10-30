package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.elitrobban.elbilsladdning.model.BilvideoSnapshot;

/** @author Robert Andersson Kopler */
public interface BilvideoRepository extends JpaRepository<BilvideoSnapshot, Long> {
}
