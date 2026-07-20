package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.elitrobban.elbilsladdning.model.EvSalesRankEntry;

import java.util.List;

public interface EvSalesRankRepository extends JpaRepository<EvSalesRankEntry, Long> {
    List<EvSalesRankEntry> findAllByOrderByRankAsc();
}
