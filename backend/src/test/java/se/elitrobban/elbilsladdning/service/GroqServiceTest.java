package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tester för GroqServices rena logik: chatt-systemprompten, rekommendations-
 * prompten, regelbaserad fallback, 429-retrytidsparsning och sektionsextraktion.
 * Inga HTTP-anrop och ingen Mockito.
 */
class GroqServiceTest {

    private final GroqService service = new GroqService();

    private static CarSpec car(String name, double ac, double dc, int range, int price) {
        return new CarSpec(name, ac, dc, List.of("ccs"), 60.0, range, price);
    }

    private static StationDto station(String name, double dist, double kw, String price) {
        return new StationDto(name, "Gatan 1", dist, 59.3, 18.0, kw, kw, "CCS",
                "Ionity", null, price, 4, "ocm1");
    }

    // --- buildChatSystemPrompt ---

    @Test
    void chattpromptenInnehallerReglerOchTopplistor() {
        List<CarSpec> cars = List.of(
                car("Tesla Model 3", 11, 250, 566, 499_000),
                car("Dacia Spring", 7, 30, 225, 180_000),
                car("Kia EV6", 11, 233, 528, 460_000));
        String p = service.buildChatSystemPrompt(cars);
        assertThat(p)
                .contains("49 kr/månad")
                .contains("BUDGET-REGLER")
                .contains("Snabbaste DC-laddning")
                .contains("Längst räckvidd");
        // Topplistorna formateras "namn: värde, pris" ("Dacia Spring" ensamt räcker inte —
        // namnet förekommer redan i den statiska budgettexten)
        assertThat(p)
                .contains("Tesla Model 3: 250 kW DC, 499 tkr")
                .contains("Tesla Model 3: 566 km, 499 tkr");
    }

    @Test
    void bilarUtanPrisEllerRackviddFiltrerasBortUrTopplistorna() {
        List<CarSpec> cars = List.of(
                car("Riktig bil", 11, 150, 400, 350_000),
                car("Okänt pris", 11, 200, 500, 0));
        String p = service.buildChatSystemPrompt(cars);
        assertThat(p).doesNotContain("Okänt pris");
    }

    // --- buildPrompt ---

    @Test
    void rekommendationspromptenListarMax5StationerMedPris() {
        CarSpec bil = car("Volvo EX30", 11, 153, 344, 320_000);
        List<StationDto> stations = List.of(
                station("Ionity Arlanda", 1.2, 350, "6,50 kr/kWh"),
                station("Circle K", 2.8, 150, null),
                station("S3", 3.0, 50, null), station("S4", 4.0, 50, null),
                station("S5", 5.0, 50, null), station("S6", 6.0, 50, null));
        String p = service.buildPrompt(bil, stations, null);
        assertThat(p)
                .contains("Volvo EX30")
                .contains("DC max 153")
                .contains("1. Ionity Arlanda")
                .contains("Pris: 6,50 kr/kWh")
                .contains("Pris: okänt")   // Circle K saknar pris
                .doesNotContain("S6");     // max 5 stationer
    }

    @Test
    void kostnadsjamforelseBifogasNarDenFinns() {
        CarSpec bil = car("Kia EV6", 11, 233, 528, 460_000);
        String p = service.buildPrompt(bil, List.of(station("X", 1, 150, null)), "EV6: 45 kr, Model 3: 42 kr");
        assertThat(p).contains("Kostnadsjämförelse").contains("EV6: 45 kr");
    }

    // --- buildFallback ---

    @Test
    void fallbackRekommenderarNarmastaStationen() {
        CarSpec bil = car("Nissan Leaf", 6.6, 50, 270, 290_000);
        GroqService.GroqResult r = service.buildFallback(bil,
                List.of(station("Vattenfall Kista", 0.8, 150, null)));
        assertThat(r.recommendation())
                .contains("Vattenfall Kista")
                .contains("Nissan Leaf");
        assertThat(r.funFact()).isNull();
    }

    @Test
    void fallbackUtanStationerGerTydligtBesked() {
        GroqService.GroqResult r = service.buildFallback(car("Zoe", 22, 50, 395, 270_000), List.of());
        assertThat(r.recommendation()).contains("Inga laddstationer");
    }

    // --- parseRetryMs ---

    @Test
    void retrytidParsasFranGroq429Kropp() {
        assertThat(service.parseRetryMs("try again in 2m59.56s")).isEqualTo((long) ((2 * 60 + 59.56) * 1000));
        assertThat(service.parseRetryMs("try again in 30s")).isEqualTo(30_000L);
        assertThat(service.parseRetryMs("garbage")).isEqualTo(15 * 60 * 1000L); // default 15 min
    }

    // --- extractSectionCI ---

    @Test
    void sektionExtraherasSkiftlagesokansligt() {
        String text = "REKOMMENDATION: Ladda vid Ionity.\nVisste du att: EV6 laddar 10-80% på 18 min.";
        assertThat(service.extractSectionCI(text, "rekommendation:", "visste du att:"))
                .isEqualTo("Ladda vid Ionity.");
        assertThat(service.extractSectionCI(text, "VISSTE DU ATT:", null))
                .isEqualTo("EV6 laddar 10-80% på 18 min.");
        assertThat(service.extractSectionCI(text, "FINNS INTE:", null)).isNull();
    }
}
