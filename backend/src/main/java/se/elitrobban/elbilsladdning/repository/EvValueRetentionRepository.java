package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.elitrobban.elbilsladdning.model.EvValueRetentionEntry;

import java.util.List;

public interface EvValueRetentionRepository extends JpaRepository<EvValueRetentionEntry, Long> {

    /** Störst värdetapp först — lägst kvarvarande andel är det som gör en bil till ett fynd. */
    List<EvValueRetentionEntry> findAllByOrderByRetentionPctAsc();
}
