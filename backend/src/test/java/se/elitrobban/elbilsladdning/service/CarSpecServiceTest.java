package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import se.elitrobban.elbilsladdning.data.CarDatabase;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.EvSpecEntity;
import se.elitrobban.elbilsladdning.repository.EvSpecRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tester för billistan: mappning från databas-entitet, deduplicering,
 * cache och fallback till den hårdkodade listan när databasen är nere.
 */
@ExtendWith(MockitoExtension.class)
class CarSpecServiceTest {

    @Mock
    private EvSpecRepository repo;

    private CarSpecService service() {
        return new CarSpecService(repo);
    }

    /** EvSpecEntity fylls normalt av JPA via reflection — testerna gör likadant. */
    private static EvSpecEntity entity(String namn, Double acKw, Double dcKw, Integer rangeKm) {
        EvSpecEntity e = new EvSpecEntity();
        ReflectionTestUtils.setField(e, "carName", namn);
        ReflectionTestUtils.setField(e, "maxAcKw", acKw);
        ReflectionTestUtils.setField(e, "maxDcKw", dcKw);
        ReflectionTestUtils.setField(e, "batteryKwh", 60.0);
        ReflectionTestUtils.setField(e, "rangeKm", rangeKm);
        ReflectionTestUtils.setField(e, "priceKr", 400_000);
        ReflectionTestUtils.setField(e, "carType", "EV");
        return e;
    }

    @Test
    void mapparEntitetTillCarSpec() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV"))
                .thenReturn(List.of(entity("Tesla Model 3", 11.0, 250.0, 500)));

        List<CarSpec> cars = service().getCars();
        assertThat(cars).hasSize(1);
        CarSpec c = cars.get(0);
        assertThat(c.name()).isEqualTo("Tesla Model 3");
        assertThat(c.maxDcKw()).isEqualTo(250.0);
        assertThat(c.rangeKm()).isEqualTo(500);
    }

    @Test
    void duplikatBehallerVariantenMedLangstRackvidd() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV")).thenReturn(List.of(
                entity("Volvo EX30", 11.0, 150.0, 340),
                entity("Volvo EX30", 11.0, 150.0, 480)));

        List<CarSpec> cars = service().getCars();
        assertThat(cars).hasSize(1);
        assertThat(cars.get(0).rangeKm()).isEqualTo(480);
    }

    @Test
    void dcBilFarType2OchCcs() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV"))
                .thenReturn(List.of(entity("Volvo EX30", 11.0, 150.0, 340)));
        assertThat(service().getCars().get(0).connectors()).containsExactly("type2", "ccs");
    }

    @Test
    void nissanLeafFarChademo() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV"))
                .thenReturn(List.of(entity("Nissan Leaf", 6.6, 50.0, 270)));
        assertThat(service().getCars().get(0).connectors()).containsExactly("type2", "chademo");
    }

    @Test
    void bilUtanDcLaddningFarBaraType2() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV"))
                .thenReturn(List.of(entity("Renault Zoe", 22.0, null, 300)));
        assertThat(service().getCars().get(0).connectors()).containsExactly("type2");
    }

    @Test
    void tomDatabasGerHardkodadFallbackLista() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV")).thenReturn(List.of());
        assertThat(service().getCars()).isSameAs(CarDatabase.CARS);
    }

    @Test
    void databasfelGerHardkodadFallbackLista() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV")).thenThrow(new RuntimeException("DB nere"));
        assertThat(service().getCars()).isSameAs(CarDatabase.CARS);
    }

    @Test
    void andraAnropetKommerFranCachen() {
        when(repo.findByCarTypeOrderByCarNameAsc("EV"))
                .thenReturn(List.of(entity("Tesla Model 3", 11.0, 250.0, 500)));

        CarSpecService s = service();
        s.getCars();
        s.getCars();
        verify(repo, times(1)).findByCarTypeOrderByCarNameAsc("EV"); // bara ett DB-anrop
    }
}
