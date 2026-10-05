package se.elitrobban.elbilsladdning.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import se.elitrobban.elbilsladdning.scraper.BlocketUsedPriceClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ny- och begagnatpris ur Blockets annonser. Annonsformen är kopierad ur Blockets riktiga svar
 * 2026-10-01.
 *
 * @author Robert Andersson Kopler
 */
class ElbilsmarknadServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode annons(String make, String model, String fuel, int pris, int ar, Integer mil) throws Exception {
        return mapper.readTree("{\"make\":\"" + make + "\",\"model\":\"" + model + "\",\"fuel\":\"" + fuel
                + "\",\"price\":{\"amount\":" + pris + "},\"year\":" + ar
                + (mil == null ? "" : ",\"mileage\":" + mil) + "}");
    }

    @Test
    void sokordetSkalarBortVersionenMenBehallerModellen() {
        assertThat(ElbilsmarknadService.sokord("Volvo EX30 Twin Motor")).isEqualTo("Volvo EX30");
        assertThat(ElbilsmarknadService.sokord("Audi A2 e-tron 125 kW - 50 kWh")).isEqualTo("Audi A2");
        assertThat(ElbilsmarknadService.sokord("Nissan Leaf 62 kWh")).isEqualTo("Nissan Leaf");
        assertThat(ElbilsmarknadService.sokord("Škoda Elroq 85")).isEqualTo("Skoda Elroq");
        assertThat(ElbilsmarknadService.sokord("Polestar 2 Long Range")).isEqualTo("Polestar 2");
        assertThat(ElbilsmarknadService.sokord("Fiat 500e Hatchback")).isEqualTo("Fiat 500e");
        assertThat(ElbilsmarknadService.sokord("Volkswagen ID.4 Pro")).isEqualTo("Volkswagen ID.4");
    }

    @Test
    void sokordetTarModellnummerSomStarSomEgetOrd() {
        // "Tesla Model" och "Hyundai Ioniq" är inga modeller — siffran eller bokstaven efter hör till.
        assertThat(ElbilsmarknadService.sokord("Tesla Model 3 Long Range")).isEqualTo("Tesla Model 3");
        assertThat(ElbilsmarknadService.sokord("Tesla Model Y Long Range")).isEqualTo("Tesla Model Y");
        assertThat(ElbilsmarknadService.sokord("Hyundai Ioniq 5 77 kWh")).isEqualTo("Hyundai Ioniq 5");
    }

    @Test
    void grannmodellerSomFritextenDrarInRaknasInte() throws Exception {
        // Mätt 2026-10-01: sökningen "Volvo EX30" gav även EX40 och EC40.
        assertThat(ElbilsmarknadService.sammaModell("Volvo EX30 Twin Motor",
                annons("Volvo", "EX30", "El", 400_000, 2025, 100))).isTrue();
        assertThat(ElbilsmarknadService.sammaModell("Volvo EX30 Twin Motor",
                annons("Volvo", "EX40", "El", 500_000, 2025, 100))).isFalse();
        assertThat(ElbilsmarknadService.sammaModell("Tesla Model 3 Long Range",
                annons("Tesla", "Model Y", "El", 400_000, 2024, 100))).isFalse();
    }

    @Test
    void stavningsskillnaderJamnasUt() throws Exception {
        assertThat(ElbilsmarknadService.sammaModell("Škoda Enyaq iV 85",
                annons("Skoda", "Enyaq", "El", 400_000, 2023, 3_000))).isTrue();
        assertThat(ElbilsmarknadService.sammaModell("Audi Q4 e-tron 45 quattro",
                annons("Audi", "Q4 e-tron", "El", 400_000, 2023, 3_000))).isTrue();
    }

    @Test
    void blocketsStavningMoterDatabasens() throws Exception {
        // Mätt 2026-10-01: Blocket skriver "Mercedes-Benz" + "EQA250", "MG" + "MG4", "ID. Buzz".
        assertThat(ElbilsmarknadService.sammaModell("Mercedes EQA 250",
                annons("Mercedes-Benz", "EQA250", "El", 400_000, 2023, 3_000))).isTrue();
        assertThat(ElbilsmarknadService.sammaModell("Mercedes EQA 250",
                annons("Mercedes-Benz", "EQA300", "El", 400_000, 2023, 3_000))).isFalse();
        assertThat(ElbilsmarknadService.sammaModell("MG4 Long Range",
                annons("MG", "MG4", "El", 250_000, 2023, 3_000))).isTrue();
        assertThat(ElbilsmarknadService.sammaModell("MG4 Long Range",
                annons("MG", "4", "El", 250_000, 2023, 3_000))).isTrue();
        assertThat(ElbilsmarknadService.sammaModell("Volkswagen ID.Buzz",
                annons("Volkswagen", "ID. Buzz", "El", 500_000, 2024, 3_000))).isTrue();
        assertThat(ElbilsmarknadService.sokord("MG4 Long Range")).isEqualTo("MG4");
    }

    @Test
    void versionsordStorFritextenOchTasBort() {
        // Mätt 2026-10-01: "Škoda Enyaq iV" gav 4 nya Enyaq, "Skoda Enyaq" 29.
        assertThat(ElbilsmarknadService.sokord("Škoda Enyaq iV 85")).isEqualTo("Skoda Enyaq");
        assertThat(ElbilsmarknadService.sokord("Kia Niro EV")).isEqualTo("Kia Niro");
    }

    @Test
    void markeSomDatabasenSkriverTvaGangerEllerUtanMellanslag() throws Exception {
        // Mätt i produktion 2026-10-01: båda gav noll träffar.
        assertThat(ElbilsmarknadService.sammaModell("MG MG4 XPOWER",
                annons("MG", "MG4", "El", 300_000, 2023, 3_000))).isTrue();
        assertThat(ElbilsmarknadService.sokord("Lynk&Co 02")).isEqualTo("Lynk & Co 02");
        assertThat(ElbilsmarknadService.sammaModell("Lynk&Co 02",
                annons("Lynk & Co", "02", "El", 350_000, 2025, 1_000))).isTrue();
    }

    @Test
    void annonsMedMerDetaljerAnDatabasenRaknas() throws Exception {
        // Blocket skriver "Taycan 4S", "Taycan GTS" — databasen bara "Porsche Taycan".
        assertThat(ElbilsmarknadService.sammaModell("Porsche Taycan",
                annons("Porsche", "Taycan 4S", "El", 700_000, 2022, 3_000))).isTrue();
        assertThat(ElbilsmarknadService.sammaModell("Porsche Taycan",
                annons("Porsche", "Macan", "El", 700_000, 2025, 300))).isFalse();
    }

    @Test
    void gamlaIoniqRaknasInteSomIoniq5() throws Exception {
        // Blocket har kvar "IONIQ" (2016–2022) som egen modell — ord för ord början av "IONIQ 5".
        assertThat(ElbilsmarknadService.sammaModell("Hyundai IONIQ 5",
                annons("Hyundai", "IONIQ", "El", 150_000, 2018, 9_000))).isFalse();
        assertThat(ElbilsmarknadService.sammaModell("Hyundai IONIQ 5",
                annons("Hyundai", "Ioniq 5", "El", 350_000, 2022, 5_000))).isTrue();
        // Flersiffriga tal efter modellen är versionen och stoppar inte.
        assertThat(ElbilsmarknadService.sammaModell("Nissan Leaf (50 kWh)",
                annons("Nissan", "Leaf", "El", 150_000, 2020, 9_000))).isTrue();
    }

    @Test
    void annatBransleRaknasInte() throws Exception {
        assertThat(ElbilsmarknadService.sammaModell("Volvo XC40 Recharge",
                annons("Volvo", "XC40", "Plug-in Bensin", 300_000, 2022, 3_000))).isFalse();
    }

    @Test
    void leasingavgiftOchSlitnaBilarHallsUtanfor() throws Exception {
        List<JsonNode> nya = List.of(
                annons("Volvo", "EX30", "El", 3_495, 2027, 0),          // privatleasing, kr/mån
                annons("Volvo", "EX30", "El", 432_000, 2027, 0),
                annons("Volvo", "EX30", "El", 460_000, 2027, 0),
                annons("Volvo", "EX30", "El", 520_000, 2027, 0),
                annons("Volvo", "EX40", "El", 600_000, 2027, 0));       // fel modell
        List<JsonNode> begagnade = List.of(
                annons("Volvo", "EX30", "El", 299_800, 2024, 13_575),
                annons("Volvo", "EX30", "El", 340_000, 2024, 5_000),
                annons("Volvo", "EX30", "El", 380_000, 2025, 2_000),
                annons("Volvo", "EX30", "El", 150_000, 2024, 20_000),    // över milgränsen
                annons("Volvo", "EX30", "El", 350_000, 2024, null));     // mätarställning saknas

        var bild = ElbilsmarknadService.berakna("Volvo EX30 Single Motor", true, nya, begagnade, 0L);

        assertThat(bild.modell()).isEqualTo("Volvo EX30");
        assertThat(bild.ny().antal()).isEqualTo(3);
        assertThat(bild.ny().billigastKr()).isEqualTo(432_000);
        assertThat(bild.ny().medianKr()).isEqualTo(460_000);
        assertThat(bild.begagnad().antal()).isEqualTo(3);
        assertThat(bild.begagnad().medianKr()).isEqualTo(340_000);
        assertThat(bild.begagnad().arsmodellFran()).isEqualTo(2024);
        assertThat(bild.begagnad().arsmodellTill()).isEqualTo(2025);
    }

    @Test
    void ettFelFranBlocketBlirInteEttPrisOchCachasInte() {
        int[] anrop = {0};
        BlocketUsedPriceClient trasig = new BlocketUsedPriceClient() {
            @Override
            public List<JsonNode> elbilsannonser(String sokord, int salesForm) {
                anrop[0]++;
                return null;
            }
        };
        ElbilsmarknadService tjanst = new ElbilsmarknadService(trasig);

        assertThat(tjanst.forBil("Volvo EX30 Twin Motor", true)).isEmpty();
        assertThat(tjanst.cachad("Volvo EX30 Twin Motor", true)).isEmpty();
        tjanst.forBil("Volvo EX30 Twin Motor", true);
        assertThat(anrop[0]).isEqualTo(4); // försöker igen nästa gång i stället för att minnas felet
    }

    @Test
    void varianterAvSammaModellDelarEnHamtning() {
        int[] anrop = {0};
        BlocketUsedPriceClient blocket = new BlocketUsedPriceClient() {
            @Override
            public List<JsonNode> elbilsannonser(String sokord, int salesForm) {
                anrop[0]++;
                return List.of();
            }
        };
        ElbilsmarknadService tjanst = new ElbilsmarknadService(blocket);

        tjanst.forBil("Volvo EX30 Single Motor", true);
        tjanst.forBil("Volvo EX30 Twin Motor", true);
        assertThat(anrop[0]).isEqualTo(2);
        assertThat(tjanst.cachad("Volvo EX30 Cross Country", true)).isPresent();
    }

    private List<JsonNode> i3Nya() throws Exception {
        return List.of(annons("BMW", "i3", "El", 750_000, 2027, 0),
                       annons("BMW", "i3", "El", 775_000, 2027, 0),
                       annons("BMW", "i3", "El", 800_000, 2026, 0));
    }

    private List<JsonNode> i3Begagnade() throws Exception {
        return List.of(annons("BMW", "i3", "El", 99_000, 2015, 9_000),
                       annons("BMW", "i3", "El", 180_000, 2019, 6_000),
                       annons("BMW", "i3", "El", 230_000, 2022, 3_000),
                       annons("BMW", "i3", "El", 720_000, 2027, 100));   // nya generationen, demobil
    }

    @Test
    void gamlaI3FarIngetNyprisOchBaraGamlaGenerationensBegagnade() throws Exception {
        // Mätt 2026-10-01: "BMW i3 120 Ah" (nedlagd 2022) visade nya generationens 775 tkr.
        var bild = ElbilsmarknadService.berakna("BMW i3 120 Ah 37.9 kWh", false, i3Nya(), i3Begagnade(), 0L);
        assertThat(bild.ny().antal()).isZero();
        assertThat(bild.begagnad().antal()).isEqualTo(3);
        assertThat(bild.begagnad().arsmodellTill()).isEqualTo(2022);
    }

    @Test
    void nyaI3FarBaraNyaGenerationen() throws Exception {
        var bild = ElbilsmarknadService.berakna("BMW i3 40 xDrive", true, i3Nya(), i3Begagnade(), 0L);
        assertThat(bild.ny().antal()).isEqualTo(3);
        assertThat(bild.begagnad().antal()).isEqualTo(1);
        assertThat(bild.begagnad().arsmodellFran()).isEqualTo(2027);
    }

    @Test
    void glesaBegagnatannonserUtanNyaBilarDelasInte() throws Exception {
        // 2014 och sedan 2019 är inget generationsskifte när inga nya bilar säljs.
        List<JsonNode> beg = List.of(annons("Renault", "Zoe", "El", 60_000, 2014, 9_000),
                                     annons("Renault", "Zoe", "El", 140_000, 2019, 6_000),
                                     annons("Renault", "Zoe", "El", 160_000, 2021, 4_000));
        var bild = ElbilsmarknadService.berakna("Renault Zoe", false, List.of(), beg, 0L);
        assertThat(bild.begagnad().antal()).isEqualTo(3);
    }

    @Test
    void sammanhangandeArsmodellerDelasInte() throws Exception {
        // Volvo EX30: begagnade 2024–2027 och nya 2026–2027 är en och samma generation.
        List<JsonNode> nya = List.of(annons("Volvo", "EX30", "El", 450_000, 2027, 0));
        List<JsonNode> beg = List.of(annons("Volvo", "EX30", "El", 330_000, 2024, 5_000),
                                     annons("Volvo", "EX30", "El", 380_000, 2025, 2_000));
        var bild = ElbilsmarknadService.berakna("Volvo EX30 Single Motor", true, nya, beg, 0L);
        assertThat(bild.ny().antal()).isEqualTo(1);
        assertThat(bild.begagnad().antal()).isEqualTo(2);
    }
}
