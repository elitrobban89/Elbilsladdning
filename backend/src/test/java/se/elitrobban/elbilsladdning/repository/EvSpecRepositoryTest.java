package se.elitrobban.elbilsladdning.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frågan mot en riktig databas (H2), inte mot en mock.
 *
 * <p>Hela poängen är ett SQL-villkor — {@code car_type IS NULL OR ...} — och ett mockat repo
 * hade svarat precis det testet matade in oavsett vad JPQL:en säger. Skarpt fall 2026-08-28:
 * tabellen delas med CarAdvice och hade 557 rader, varav 58 med {@code car_type = NULL}.
 * CarAdvice läste dem som elbilar, den här tjänsten frågade efter {@code = 'EV'} och tappade
 * 58 bilar — Ioniq 5, Kia EV9, Mustang Mach-E, Volvo EX60. Det syntes ingenstans i loggarna;
 * bilarna fanns bara inte i väljaren.
 */
@DataJpaTest
class EvSpecRepositoryTest {

    @Autowired
    private EvSpecRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    private void rad(String namn, String typ) {
        jdbc.update("INSERT INTO ev_spec (car_name, car_type, battery_kwh, range_km, max_dc_kw,"
                + " max_ac_kw, price_kr) VALUES (?, ?, ?, ?, ?, ?, ?)",
                namn, typ, 77.0, 500, 150.0, 11.0, 400000);
    }

    @Test
    void nullITypenRaknasSomElbil() {
        rad("Typad Elbil", "EV");
        rad("Otypad Elbil", null);
        rad("Laddhybrid", "PHEV");

        List<String> namn = repo.findElbilar().stream().map(EvSpecEntityNamn::av).toList();

        assertThat(namn).contains("Typad Elbil", "Otypad Elbil");
        // Laddhybriderna är korrekt typade och ska INTE glida in bara för att NULL släpps fram
        assertThat(namn).doesNotContain("Laddhybrid");
    }

    @Test
    void gamlaFraganTapparDeOtypade() {
        rad("Typad Elbil", "EV");
        rad("Otypad Elbil", null);

        // Dokumenterar felet som fanns: den här frågan är kvar i repot och används av andra
        // anropare, men den får aldrig mer vara den som bygger billistan.
        List<String> namn = repo.findByCarTypeOrderByCarNameAsc("EV").stream()
                .map(EvSpecEntityNamn::av).toList();

        assertThat(namn).contains("Typad Elbil");
        assertThat(namn).doesNotContain("Otypad Elbil");
    }

    /** Liten hjälpare: entiteten är @Immutable och har bara getters. */
    private static final class EvSpecEntityNamn {
        static String av(se.elitrobban.elbilsladdning.model.EvSpecEntity e) { return e.getCarName(); }
    }
}
