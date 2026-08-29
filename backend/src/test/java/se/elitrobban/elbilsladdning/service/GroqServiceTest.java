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
    void chattpromptenKraverUttryckligBekraftelseNarBilenKlararUtanStopp() {
        String p = service.buildChatSystemPrompt(List.of(car("Tesla Model 3", 11, 250, 566, 499_000)));
        // Regeln ska tvinga en tydlig bekraftelse (inte bara "lyft det positivt")
        assertThat(p)
                .contains("Bilen klarar sträckan utan laddningsstopp")
                .contains("MÅSTE du inleda")
                .contains("utan laddstopp");
    }

    @Test
    void bilarUtanPrisEllerRackviddFiltrerasBortUrTopplistorna() {
        List<CarSpec> cars = List.of(
                car("Riktig bil", 11, 150, 400, 350_000),
                car("Okänt pris", 11, 200, 500, 0));
        String p = service.buildChatSystemPrompt(cars);
        assertThat(p).doesNotContain("Okänt pris");
    }

    @Test
    void chattpromptenForbjuderGissatBatteriUrModellnamnet() {
        // Skarpt fall 2026-08-29: UTAN kontext svarade assistenten "1,2-1,3 timmar" för en
        // Volvo EX60 på en 50 kW-laddare — räknat på 60 kWh hämtat ur namnet. Bilen har
        // 112 kWh och tar 2 tim 14 min, vilket samma fråga MED kontext också svarade.
        String p = service.buildChatSystemPrompt(List.of(car("Tesla Model 3", 11, 250, 566, 499_000)));
        assertThat(p)
                .contains("Härled ALDRIG batteriets storlek ur modellnamnet")
                .contains("Gissa inte");
    }

    @Test
    void chattpromptenRaknarModellerIStalletForAttPastaEttFastTal() {
        // Stod "73 modeller i databasen" medan tabellen bar 520.
        String p = service.buildChatSystemPrompt(List.of(
                car("Tesla Model 3", 11, 250, 566, 499_000),
                car("Kia EV6", 11, 233, 528, 460_000)));
        assertThat(p).contains("BILDATA (2 modeller").doesNotContain("73 modeller");
    }

    @Test
    void topplistornaBarBatteristorleken() {
        // Batteriet fanns inte på någon rad i prompten — och det är just det som gissas.
        String p = service.buildChatSystemPrompt(List.of(car("Tesla Model 3", 11, 250, 566, 499_000)));
        assertThat(p)
                .contains("Tesla Model 3: 250 kW DC, 499 tkr, 60 kWh batteri")
                .contains("Tesla Model 3: 566 km, 499 tkr, 60 kWh batteri");
    }

    // --- uppslagning av bilar användaren nämner vid namn ----------------------

    private static CarSpec bil(String name, double ac, double dc, double batteri, int range, int price) {
        return new CarSpec(name, ac, dc, List.of("ccs"), batteri, range, price);
    }

    private static java.util.List<java.util.Map<String, String>> fran(String... rader) {
        java.util.List<java.util.Map<String, String>> ut = new java.util.ArrayList<>();
        for (String r : rader) ut.add(java.util.Map.of("role", "user", "content", r));
        return ut;
    }

    private static final CarSpec EX60 = bil("Volvo EX60", 22, 370, 112, 810, 620_000);

    @Test
    void bilenHittasPaHelaNamnet() {
        assertThat(GroqService.namndaBilar("hur lång tid tar det att ladda en Volvo EX60 med 50 kW?",
                List.of(EX60))).extracting(CarSpec::name).containsExactly("Volvo EX60");
    }

    @Test
    void bilenHittasAvenUtanMarkesordet() {
        // Man skriver sällan ut märket i en följdfråga.
        assertThat(GroqService.namndaBilar("vad har EX60 för batteri?", List.of(EX60))).hasSize(1);
    }

    @Test
    void frageteckenOchVersalerSpelarIngenRoll() {
        assertThat(GroqService.namndaBilar("VOLVO EX60?!", List.of(EX60))).hasSize(1);
    }

    @Test
    void ensamSiffraDrarInteInEnBil() {
        // "Polestar 2" har ingen egen beteckning efter märket, så siffran ensam får inte räcka -
        // annars hade varje mening med en tvåa i sig dragit in bilen.
        List<CarSpec> bilar = List.of(bil("Polestar 2", 11, 207, 79, 659, 510_000));
        assertThat(GroqService.namndaBilar("jag vill ladda till 2 procent över natten", bilar)).isEmpty();
        assertThat(GroqService.namndaBilar("hur är Polestar 2?", bilar)).hasSize(1);
    }

    @Test
    void generisktModellordDrarInteInHelaMarket() {
        // "model" står på var enda Tesla-rad och identifierar därför ingen bil.
        List<CarSpec> bilar = List.of(
                bil("Tesla Model 3", 11, 250, 60, 534, 450_000),
                bil("Tesla Model Y", 11, 250, 60, 534, 500_000));
        assertThat(GroqService.namndaBilar("vilken model passar mig bäst?", bilar)).isEmpty();
        assertThat(GroqService.namndaBilar("berätta om Model 3", bilar))
                .extracting(CarSpec::name).containsExactly("Tesla Model 3");
    }

    @Test
    void modellenIForstaOrdetNasOcksa() {
        // MG4 heter så i första ordet, och orden efter ("Long Range") är generiska. Utan
        // märkesregeln hade en fråga om MG4 inte träffat en enda av raderna.
        List<CarSpec> bilar = List.of(
                bil("MG4 Long Range", 11, 144, 64, 450, 300_000),
                bil("MG4 Standard Range", 11, 82, 51, 350, 260_000));
        assertThat(GroqService.namndaBilar("vad kostar en MG4?", bilar)).hasSize(2);
    }

    @Test
    void jamforelsefraganGerBadaBilarnaTrotsTaket() {
        // Uppmätt mot de riktiga namnen: Enyaq + ID.4 ger 12 traffar. En rak lista hade fyllt
        // taket med Enyaq-varianter och kapat bort ID.4 ur en fråga som gäller båda.
        List<CarSpec> bilar = List.of(
                bil("Škoda Enyaq 60", 11, 105, 58, 455, 400_000),
                bil("Škoda Enyaq 85", 11, 165, 77, 582, 450_000),
                bil("Škoda Enyaq RS", 11, 165, 77, 568, 500_000),
                bil("Škoda Enyaq Coupe 60", 11, 105, 58, 462, 420_000),
                bil("Škoda Enyaq Coupe 85", 11, 165, 77, 591, 470_000),
                bil("Škoda Enyaq Coupe RS", 11, 165, 77, 575, 520_000),
                bil("Volkswagen ID.4", 11, 165, 79, 570, 430_000),
                bil("Volkswagen ID.4 Pro 77 kWh", 11, 135, 77, 481, 410_000));
        List<CarSpec> traffar = GroqService.namndaBilar(
                "vad är skillnaden mellan Škoda Enyaq och en ID.4?", bilar);
        assertThat(traffar).hasSize(GroqService.MAX_NAMNDA_BILAR);
        assertThat(traffar).extracting(CarSpec::name).anyMatch(n -> n.startsWith("Škoda Enyaq"));
        assertThat(traffar).extracting(CarSpec::name).anyMatch(n -> n.startsWith("Volkswagen ID.4"));
    }

    @Test
    void bilenSomStavadesUtStarForst() {
        // "Audi Q4 e-tron" drar även in e-tron GT på ordet e-tron; den efterfrågade bilen
        // ska inte kunna trängas undan av den.
        List<CarSpec> bilar = List.of(
                bil("Audi e-tron GT quattro", 11, 270, 93, 488, 900_000),
                bil("Audi Q4 e-tron", 11, 135, 77, 528, 500_000));
        assertThat(GroqService.namndaBilar("berätta om Audi Q4 e-tron", bilar))
                .first().extracting(CarSpec::name).isEqualTo("Audi Q4 e-tron");
    }

    @Test
    void blocketBarBatteriRackviddOchSaknadSnabbladdning() {
        String block = service.namndaBilarBlock(fran("hur är Renault Zoe 22 kWh?"),
                List.of(bil("Renault Zoe 22 kWh", 22, 0, 22, 130, 0)));
        assertThat(block)
                .contains("NÄMNDA BILAR")
                .contains("22 kWh batteri")
                .contains("130 km WLTP")
                .contains("ingen snabbladdning")
                .doesNotContain("DC max 0");
    }

    @Test
    void blocketBarHelaSpecenForEnEfterfragadBil() {
        String block = service.namndaBilarBlock(fran("hur länge laddar en EX60 med 50 kW?"), List.of(EX60));
        assertThat(block).contains("Volvo EX60: 112 kWh batteri")
                .contains("810 km WLTP")
                .contains("DC max 370 kW")
                .contains("620 tkr");
    }

    @Test
    void baraAnvandarensEgnaRaderGenomsoks() {
        // Assistentens egna svar räknas inte - annars hade en bil den själv råkat nämna dragit
        // in sina specar och bekräftat sig själv.
        var historik = List.of(
                java.util.Map.of("role", "assistant", "content", "Volvo EX60 är en stor SUV"),
                java.util.Map.of("role", "user", "content", "tack!"));
        assertThat(service.namndaBilarBlock(historik, List.of(EX60))).isEmpty();
    }

    @Test
    void tomtBlockNarIngenBilNamns() {
        assertThat(service.namndaBilarBlock(fran("kostar det mycket att ladda hemma?"), List.of(EX60))).isEmpty();
        assertThat(service.namndaBilarBlock(null, List.of(EX60))).isEmpty();
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
