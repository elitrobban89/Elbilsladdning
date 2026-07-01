package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.elitrobban.elbilsladdning.model.EvSpecEntity;

import java.util.List;

public interface EvSpecRepository extends JpaRepository<EvSpecEntity, Long> {
    List<EvSpecEntity> findByCarTypeOrderByCarNameAsc(String carType);
}
