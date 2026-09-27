package se.elitrobban.elbilsladdning.controller;

import org.junit.jupiter.api.Test;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laddtidskalkylatorns snabbladdare (2026-09-27). I centrala Jönköping var alla fem närmaste
 * stationer AC-laddare, och kalkylatorn skrev "Ingen DC-station hittad" fast en 85 kW-laddare
 * låg 1,6 km bort - den fanns i hela listan, bara inte bland de fem som visades.
 *
 * @author Robert Andersson Kopler
 */
class NarmasteDcTest {

    private static StationDto st(String namn, double km, double kw, String typ) {
        return new StationDto(namn, "", km, 0, 0, kw, kw, typ, "", "", null, 0, "1");
    }

    @Test
    void narmasteDcHittasAvenBakomFemAcLaddare() {
        var lista = List.of(
                st("Skolgården", 0.15, 11, "Type 2 (AC)"), st("Torget", 0.3, 22, "Type 2 (AC)"),
                st("P-hus", 0.5, 11, "Type 2 (AC)"), st("Kyrkan", 0.7, 11, "Type 2 (AC)"),
                st("Parken", 0.9, 22, "Type 2 (AC)"),
                st("Atteviks - Virta", 1.6, 85, "CCS Combo 2 (DC)"),
                st("Längre bort", 4.2, 150, "CCS Combo 2 (DC)"));
        assertThat(ChargingController.narmasteDc(lista).name()).isEqualTo("Atteviks - Virta");
    }

    @Test
    void dcUtanKandEffektRaknasInte() {
        // en DC-rad med 0 kW går inte att räkna laddtid mot
        var lista = List.of(st("Okänd", 0.2, 0, "CCS Combo 2 (DC)"), st("Känd", 2.0, 50, "CHAdeMO (DC)"));
        assertThat(ChargingController.narmasteDc(lista).name()).isEqualTo("Känd");
    }

    @Test
    void ingenDcGerNull() {
        assertThat(ChargingController.narmasteDc(List.of(st("AC", 0.1, 11, "Type 2 (AC)")))).isNull();
        assertThat(ChargingController.narmasteDc(List.of())).isNull();
    }
}
