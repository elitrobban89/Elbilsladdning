package se.elitrobban.elbilsladdning.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Annonsfiltret bakom värdetappslistan.
 *
 * <p>Det här är den farliga delen: varje annons som slinker igenom fel förgiftar medianen, och
 * en median som ser rimlig ut men bygger på fel bilar går inte att upptäcka i efterhand. Alla
 * gränser nedan är mätta mot Blockets riktiga svar 2026-08-18.
 */
class BlocketUsedPriceClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final BlocketUsedPriceClient klient = new BlocketUsedPriceClient();

    private JsonNode annons(String json) throws Exception {
        return mapper.readTree(json);
    }

    private JsonNode annons(int pris, int ar, int mil, String rubrik) throws Exception {
        // fuel "El" i hjälparen: bränslefiltret provas för sig i laddhybridtestet nedan
        return annons("{\"price\":{\"amount\":" + pris + "},\"year\":" + ar
                + ",\"mileage\":" + mil + ",\"fuel\":\"El\",\"model_specification\":\"" + rubrik + "\"}");
    }

    @Test
    void slapperIgenomEnVanligDugligAnnons() throws Exception {
        assertThat(klient.duglittPris(annons(189_000, 2021, 8_400, "Nissan Leaf e+ 62 kWh"), 2021, null))
                .isEqualTo(189_000);
    }

    @Test
    void fellArsmodellFallerAvenNarBlocketBadeOmDet() throws Exception {
        /*
         * Sökningen skickar year_from/year_to, men årsmodellen filtreras OM ändå. Blocket har
         * visat sig ignorera filterparametrar tyst förr — mileage_to gör det, mätt i CarAdvice
         * 2026-08-08 — och ett enda fel år drar medianen åt fel håll utan att synas.
         */
        assertThat(klient.duglittPris(annons(189_000, 2022, 8_400, "Nissan Leaf"), 2021, null)).isNull();
        assertThat(klient.duglittPris(annons(189_000, 2020, 8_400, "Nissan Leaf"), 2021, null)).isNull();
    }

    @Test
    void slitnaExemplarFallerPaMilgransen() throws Exception {
        /*
         * Utan milgräns sätts prisbilden av marknadens mest slitna bilar. Mätt i CarAdvice
         * 2026-08-08: en Enyaq visades från 229 900 kr — 21 091 mil — medan billigaste under
         * 10 000 mil kostade 339 900 kr. Gränsen här är 15 000 mil, för en femårig bil har
         * normalt kört mer än 10 000.
         */
        assertThat(klient.duglittPris(annons(120_000, 2021, 15_000, "Leaf"), 2021, null)).isEqualTo(120_000);
        assertThat(klient.duglittPris(annons(120_000, 2021, 15_001, "Leaf"), 2021, null)).isNull();
        assertThat(klient.duglittPris(annons(120_000, 2021, 24_000, "Leaf"), 2021, null)).isNull();
    }

    @Test
    void manadsavgifterOchFelskrivnaPriserFaller() throws Exception {
        // Leasingannonser filtreras bort med sales_form redan i sökningen, men ett prisfält som
        // bär en månadsavgift är ändå det klassiska sättet att få en absurd median.
        assertThat(klient.duglittPris(annons(3_995, 2021, 5_000, "Leaf privatleasing"), 2021, null)).isNull();
        assertThat(klient.duglittPris(annons(39_999, 2021, 5_000, "Leaf"), 2021, null)).isNull();
        assertThat(klient.duglittPris(annons(40_000, 2021, 5_000, "Leaf"), 2021, null)).isEqualTo(40_000);
    }

    @Test
    void saknadeFaltGerNullIStalletForKrasch() throws Exception {
        assertThat(klient.duglittPris(annons("{\"year\":2021,\"mileage\":5000,\"fuel\":\"El\"}"), 2021, null)).isNull();
        assertThat(klient.duglittPris(annons("{\"price\":{\"amount\":189000},\"year\":2021,\"fuel\":\"El\"}"), 2021, null)).isNull();
        assertThat(klient.duglittPris(annons("{}"), 2021, null)).isNull();
    }

    @Test
    void trimfiltretSkiljerVarianterAtSaProcentenJamforsMotRattNypris() throws Exception {
        /*
         * Kärnan i hela mätningen. Fritextsökningen "Nissan Leaf" ger BÅDE 40 kWh och e+ 62,
         * medan nypriset vi jämför mot (461 500 kr) gäller e+. Utan filtret blandas billigare
         * 40-kWh-bilar in och värdetappet ser större ut än det är — ett fel som ser trovärdigt
         * ut och därför aldrig ifrågasätts.
         */
        assertThat(klient.duglittPris(annons(189_000, 2021, 8_000, "62 kWh N-Connecta"), 2021, "62"))
                .isEqualTo(189_000);
        assertThat(klient.duglittPris(annons(139_000, 2021, 8_000, "40 kWh Tekna"), 2021, "62"))
                .isNull();
    }

    @Test
    void trimfiltretLaserModelSpecificationOchIngenRubrik() throws Exception {
        /*
         * FÄLTVALET ÄR HELA POÄNGEN och kostade en mätning 2026-08-18. Första versionen läste
         * subject och heading — men subject finns inte alls hos Blocket, och heading är bara
         * "BMW i3" eller "Nissan Leaf", alltså modellen utan version. Filtret gav då NOLL
         * träffar för fem av sex modeller: ett filter som tyst kastade allt.
         *
         * Versionen står i model_specification: "s 120 Ah Comfort Advanced", "62 kWh
         * N-Connecta", "Pro Performance 204hk".
         */
        assertThat(BlocketUsedPriceClient.varianttextMatchar(
                annons("{\"heading\":\"BMW i3\",\"model_specification\":\"s 120 Ah Comfort Advanced\"}"), "120 ah")).isTrue();
        assertThat(BlocketUsedPriceClient.varianttextMatchar(
                annons("{\"heading\":\"Nissan Leaf\",\"model_specification\":\"62 kWh N-Connecta\"}"), "62")).isTrue();
        // 40 kWh-Leafen ska inte jämföras mot e+ 62:ans nypris
        assertThat(BlocketUsedPriceClient.varianttextMatchar(
                annons("{\"heading\":\"Nissan Leaf\",\"model_specification\":\"40 kWh Acenta\"}"), "62")).isFalse();
        // Skiftlägesokänsligt, och facade_title duger som reserv
        assertThat(BlocketUsedPriceClient.varianttextMatchar(
                annons("{\"facade_title\":\"VOLKSWAGEN ID.4 PRO\"}"), "pro")).isTrue();
    }

    @Test
    void laddhybriderFallerPaBranslefiltret() throws Exception {
        /*
         * RADEN SOM RÄDDADE MÄTNINGEN. Sökningen "Volvo XC40 Recharge" gav 2026-08-18
         * fyrtiotre annonser för 2021 — och de flesta var LADDHYBRIDER: model_specification
         * löd "Recharge T4 Inscription" och "Recharge T5 R-Design", där T4/T5 är
         * PHEV-beteckningar. Volvo kallar både laddhybrid och elbil för "Recharge".
         *
         * Utan bränslefiltret hade en fyndlista över ELBILAR räknat medianen på
         * laddhybridpriser, och siffran hade sett fullt rimlig ut. Med filtret återstod
         * fyra rena elbilar — under MIN_ANNONSER, så modellen hoppas över i stället.
         */
        assertThat(BlocketUsedPriceClient.arElbil(annons("{\"fuel\":\"El\"}"))).isTrue();
        assertThat(BlocketUsedPriceClient.arElbil(annons("{\"fuel\":\"el\"}"))).isTrue();
        assertThat(BlocketUsedPriceClient.arElbil(annons("{\"fuel\":\"El/Bensin\"}"))).isFalse();
        assertThat(BlocketUsedPriceClient.arElbil(annons("{\"fuel\":\"Diesel\"}"))).isFalse();
        assertThat(BlocketUsedPriceClient.arElbil(annons("{}"))).isFalse();

        // ...och hela vägen genom duglittPris: en T5-laddhybrid ger null
        assertThat(klient.duglittPris(annons(
                "{\"price\":{\"amount\":299900},\"year\":2021,\"mileage\":8000,"
              + "\"fuel\":\"El/Bensin\",\"model_specification\":\"Recharge T5 R-Design\"}"), 2021, null)).isNull();
    }

    @Test
    void utanTrimordSlapperAllaVarianterIgenom() throws Exception {
        // Modeller som bara sålts i en version (Renault Zoe 52, Kia e-Niro 64) har trimOrd null
        assertThat(klient.duglittPris(annons(159_900, 2021, 9_000, "Renault Zoe R135"), 2021, null))
                .isEqualTo(159_900);
    }
}
