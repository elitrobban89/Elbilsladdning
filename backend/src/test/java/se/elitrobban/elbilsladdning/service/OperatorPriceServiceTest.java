package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tester för operatörsprislistan. Ren logik utan beroenden —
 * matchning på operatörsnamn med stationsnamn som reserv.
 */
class OperatorPriceServiceTest {

    private final OperatorPriceService service = new OperatorPriceService();

    @Test
    void matcharOperatorSkiftlagesokansligt() {
        assertThat(service.getApproxPrice("IONITY GmbH", null)).isEqualTo("~6,96 kr/kWh");
    }

    @Test
    void fallerTillbakaPaStationsnamn() {
        assertThat(service.getApproxPrice("Okänd operatör AB", "Tesla Supercharger Halmstad"))
                .isEqualTo("~4,50 kr/kWh");
    }

    @Test
    void okandOperatorOchStationGerNull() {
        assertThat(service.getApproxPrice("Lokal BRF", "Garage Norr")).isNull();
    }

    @Test
    void nullVardenGerNull() {
        assertThat(service.getApproxPrice(null, null)).isNull();
    }

    @Test
    void ocmPlaceholderIgnoreras() {
        // "(Unknown Operator)" är OCM:s platshållare och får inte prismatchas,
        // men stationsnamnet ska fortfarande provas
        assertThat(service.getApproxPrice("(Unknown Operator)", "Lidl Kungsbacka"))
                .isEqualTo("~2,99 kr/kWh");
    }

    @Test
    void merSpecifikOperatorVinnerOverGenerell() {
        // "kungsbacka volvo" ligger före mindre specifika nycklar i listan
        assertThat(service.getApproxPrice("Kungsbacka Volvo Bil AB", null))
                .isEqualTo("~6,35 kr/kWh");
    }

    @Test
    void easyparkMatcharBetalplattformen() {
        assertThat(service.getApproxPrice("Easypark", null)).isEqualTo("~6,90 kr/kWh");
    }

    @Test
    void ikeaVisarGratisForKunder() {
        assertThat(service.getApproxPrice("IKEA Kållered", null)).isEqualTo("Gratis (för kunder)");
    }

    // --- parseKr ---

    @Test
    void parseKrLaserSvensktDecimalkomma() {
        assertThat(service.parseKr("~6,96 kr/kWh")).isEqualTo(6.96);
    }

    @Test
    void parseKrGerNullForGratisOchNull() {
        assertThat(service.parseKr("Gratis (för kunder)")).isNull();
        assertThat(service.parseKr(null)).isNull();
        assertThat(service.parseKr("")).isNull();
    }

    // --- nationalAverageKr ---

    @Test
    void riksgenomsnittLiggerIRimligtSpann() {
        double avg = service.nationalAverageKr();
        // Tabellvärdena spänner ~2,99–6,96 kr/kWh — snittet måste ligga däremellan
        assertThat(avg).isBetween(2.99, 6.96);
    }

    // --- billigast / dyrast ---

    @Test
    void ytterligheternaHittarBilligastOchDyrast() {
        var billig = service.billigast();
        var dyr    = service.dyrast();

        assertThat(billig).isNotNull();
        assertThat(dyr).isNotNull();
        assertThat(billig.kr()).isLessThan(dyr.kr());
        // Ingen rad i tabellen får ligga utanför de två ytterligheterna
        assertThat(service.nationalAverageKr()).isBetween(billig.kr(), dyr.kr());
    }

    @Test
    void gratisraderRaknasInteSomBilligast() {
        // "Gratis (för kunder)" är ingen prisuppgift utan en annan sorts uppgift — räknas den
        // som 0 kr blir den alltid billigast, och faktaraden hade sagt att billigaste
        // laddningen kostar noll i stället för att peka ut det billigaste NÄTVERKET.
        assertThat(service.billigast().kr()).isGreaterThan(0);
    }

    @Test
    void visningsnamnetSkrivsSomNatverketStavarSig() {
        assertThat(OperatorPriceService.visningsnamn("lidl")).isEqualTo("Lidl");
        assertThat(OperatorPriceService.visningsnamn("circle k")).isEqualTo("Circle K");
        // Nycklar med egen stavning får inte bli "E.on" respektive "Incharge"
        assertThat(OperatorPriceService.visningsnamn("e.on")).isEqualTo("E.ON");
        assertThat(OperatorPriceService.visningsnamn("incharge")).isEqualTo("InCharge");
    }

    @Test
    void riksgenomsnittAvrundasTillTvaDecimaler() {
        double avg = service.nationalAverageKr();
        assertThat(Math.round(avg * 100) / 100.0).isEqualTo(avg);
    }
}
