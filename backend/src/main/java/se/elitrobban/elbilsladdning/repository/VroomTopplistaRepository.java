package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.elitrobban.elbilsladdning.model.VroomTopplistaSnapshot;

/** @author Robert Andersson Kopler */
public interface VroomTopplistaRepository extends JpaRepository<VroomTopplistaSnapshot, Long> {
}
