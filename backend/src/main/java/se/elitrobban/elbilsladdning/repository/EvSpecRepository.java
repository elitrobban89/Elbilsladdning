package se.elitrobban.elbilsladdning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import se.elitrobban.elbilsladdning.model.EvSpecEntity;

import java.util.List;

public interface EvSpecRepository extends JpaRepository<EvSpecEntity, Long> {
    List<EvSpecEntity> findByCarTypeOrderByCarNameAsc(String carType);

    /**
     * Elbilar — inklusive raderna där {@code car_type} är NULL.
     *
     * <p>Tabellen delas med CarAdvice, och de två tjänsterna tolkade NULL olika. Mätt
     * 2026-08-28: 557 rader, varav EV 462, PHEV 37 och <b>NULL 58</b>. CarAdvice läser NULL
     * som elbil och ser alla 520; den här tjänsten frågade efter {@code car_type = 'EV'} och
     * tappade därför 58 bilar — Hyundai Ioniq 5, Kia EV9, Ford Mustang Mach-E, BMW iX3, Cupra
     * Born, Volvo EX60 och EX40 bland dem.
     *
     * <p><b>Läsaren lagas, inte datan.</b> Att fylla i de 58 raderna hade tagit bort dagens
     * symptom men lämnat orsaken: nästa rad som skrivs utan typ försvinner igen, lika tyst.
     * Tabellen är dessutom delad, så en UPDATE här är en ändring i ett annat projekts data.
     *
     * <p>NULL är säkert att läsa som elbil: alla 58 har DC-laddning, och minsta batteriet är
     * 37,3 kWh medan en laddhybrid ligger under ~20. Laddhybriderna är redan korrekt typade
     * som PHEV, alla 37 — NULL betyder alltså "ingen skrev typen", inte "okänd drivlina".
     */
    @Query("SELECT e FROM EvSpecEntity e WHERE e.carType IS NULL OR UPPER(e.carType) = 'EV'"
            + " ORDER BY e.carName ASC")
    List<EvSpecEntity> findElbilar();
}
