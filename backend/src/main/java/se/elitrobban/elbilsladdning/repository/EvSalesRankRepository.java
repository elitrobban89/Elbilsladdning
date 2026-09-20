package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.elitrobban.elbilsladdning.model.EvSalesRankEntry;

import java.util.List;

/** @author Robert Andersson Kopler */
public interface EvSalesRankRepository extends JpaRepository<EvSalesRankEntry, Long> {
    List<EvSalesRankEntry> findAllByOrderByRankAsc();
}
