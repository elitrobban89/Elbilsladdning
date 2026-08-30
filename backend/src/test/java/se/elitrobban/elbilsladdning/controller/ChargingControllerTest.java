package se.elitrobban.elbilsladdning.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;
import se.elitrobban.elbilsladdning.service.ApiNinjasService;
import se.elitrobban.elbilsladdning.service.CarSpecService;
import se.elitrobban.elbilsladdning.service.ChargepriceService;
import se.elitrobban.elbilsladdning.service.GroqService;
import se.elitrobban.elbilsladdning.service.NobilService;
import se.elitrobban.elbilsladdning.service.OcmService;
import se.elitrobban.elbilsladdning.service.OperatorPriceService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-lagertester för ChargingController: billistans form, bilindexvalidering,
 * prisberikning i stationsflödet, chattens tomma meddelanden och rate limit.
 * Alla tjänster mockas — inga externa anrop.
 */
@WebMvcTest(ChargingController.class)
class ChargingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private OcmService ocm;
    @MockBean private GroqService groq;
    @MockBean private ChargepriceService chargeprice;
    @MockBean private ApiNinjasService apiNinjas;
    @MockBean private OperatorPriceService operatorPrices;
    @MockBean private NobilService nobil;
    @MockBean private CarSpecService carSpecService;

    // Minst två bilar med räckvidd — kostnadsjämförelsen kräver att det finns "andra" bilar.
    private static final List<CarSpec> CARS = List.of(
            new CarSpec("Tesla Model 3", 11, 250, List.of("type2", "ccs"), 60, 510, 550_000),
            new CarSpec("Nissan Leaf",   6.6, 50, List.of("type2", "chademo"), 39, 270, 380_000),
            new CarSpec("Renault Zoe",   22,  46, List.of("type2", "ccs"), 52, 395, 400_000));

    private static final StationDto STATION = new StationDto(
            "Ionity Arlanda", "Arlandavägen 1", 1.2, 59.65, 17.93, 250, 350,
            "ccs", "Ionity", null, null, 0, "12345");

    @Test
    void billistanInnehallerSpecar() throws Exception {
        when(carSpecService.getCars()).thenReturn(CARS);

        mvc.perform(get("/api/cars"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(3))
           .andExpect(jsonPath("$[0].name").value("Tesla Model 3"))
           .andExpect(jsonPath("$[0].rangeKm").value(510))
           .andExpect(jsonPath("$[1].connectors[1]").value("chademo"));
    }

    @Test
    void ogiltigtBilindexGer400() throws Exception {
        when(carSpecService.getCars()).thenReturn(CARS);

        mvc.perform(get("/api/stations")
                .header("X-Forwarded-For", "10.1.1.1")
                .param("lat", "59.33").param("lon", "18.06").param("carIndex", "99"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("Ogiltigt bilindex: 99"));
    }

    @Test
    void stationsflodetBerikarPrisFranOperatorstabellen() throws Exception {
        when(carSpecService.getCars()).thenReturn(CARS);
        when(ocm.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of(STATION));
        when(nobil.getStations(anyDouble(), anyDouble())).thenReturn(List.of());
        when(chargeprice.getPricePerKwh(any(), any())).thenReturn(null);
        when(apiNinjas.isEnabled()).thenReturn(false);
        when(operatorPrices.getApproxPrice("Ionity", "Ionity Arlanda")).thenReturn("ca 8,75 kr/kWh");
        when(groq.recommend(any(), any(), any()))
                .thenReturn(new GroqService.GroqResult("Ladda på Ionity Arlanda.", "Visste du att..."));

        mvc.perform(get("/api/stations")
                .header("X-Forwarded-For", "10.2.2.2")
                .param("lat", "59.33").param("lon", "18.06").param("carIndex", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carName").value("Tesla Model 3"))
           .andExpect(jsonPath("$.stations.length()").value(1))
           .andExpect(jsonPath("$.stations[0].chargepricePerKwh").value("ca 8,75 kr/kWh"))
           .andExpect(jsonPath("$.recommendation").value("Ladda på Ionity Arlanda."))
           .andExpect(jsonPath("$.carFact").isNotEmpty());
    }

    /**
     * NOBIL bidrar bara med antalet kontakter per station. Före 2026-08-30 låg båda källorna
     * i ETT try, så ett NOBIL-fel tömde OCM:s stationslista och svaret blev noll stationer
     * med HTTP 200 — omöjligt att skilja från "inga laddare i närheten". Provet fäller
     * återgången: stationen ska stå kvar, och kontakträkningen falla tillbaka på OCM:s tal.
     */
    @Test
    void nobilSomFallerTarInteMedSigStationerna() throws Exception {
        when(carSpecService.getCars()).thenReturn(CARS);
        when(ocm.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of(STATION));
        when(nobil.getStations(anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Connect timed out"));
        when(chargeprice.getPricePerKwh(any(), any())).thenReturn(null);
        when(apiNinjas.isEnabled()).thenReturn(false);
        when(operatorPrices.getApproxPrice(any(), any())).thenReturn(null);
        when(groq.recommend(any(), any(), any()))
                .thenReturn(new GroqService.GroqResult("Ladda på Ionity Arlanda.", "Visste du att..."));

        mvc.perform(get("/api/stations")
                .header("X-Forwarded-For", "10.9.9.9")
                .param("lat", "59.33").param("lon", "18.06").param("carIndex", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.stations.length()").value(1))
           .andExpect(jsonPath("$.stations[0].name").value("Ionity Arlanda"))
           .andExpect(jsonPath("$.stations[0].connectorCount").value(0));
    }

    /**
     * Faller OCM finns det ingen lista att visa — då är tomt rätt svar, men det ska SÄGAS.
     * En tom lista utan förklaring läser som "det finns inga laddare här", vilket är ett
     * annat och felaktigt besked.
     */
    @Test
    void ocmSomFallerSagerAttKallanFallerade() throws Exception {
        when(carSpecService.getCars()).thenReturn(CARS);
        when(ocm.findNearby(anyDouble(), anyDouble(), any()))
                .thenThrow(new RuntimeException("403 Forbidden"));
        when(nobil.getStations(anyDouble(), anyDouble())).thenReturn(List.of());
        when(groq.recommend(any(), any(), any()))
                .thenReturn(new GroqService.GroqResult("Inga laddare hittades.", ""));

        mvc.perform(get("/api/stations")
                .header("X-Forwarded-For", "10.9.9.10")
                .param("lat", "59.33").param("lon", "18.06").param("carIndex", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.stations.length()").value(0))
           .andExpect(jsonPath("$.sourceError").isNotEmpty());
    }

    /**
     * Andra hållet, och det är det som gör fältet användbart: en källa som SVARAR med noll
     * stationer är inget haveri, och då får inget felmeddelande skickas med. Slås de två
     * ihop blir varningen brus och slutar betyda något.
     */
    @Test
    void nollStationerUtanFelGerIngetFelmeddelande() throws Exception {
        when(carSpecService.getCars()).thenReturn(CARS);
        when(ocm.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of());
        when(nobil.getStations(anyDouble(), anyDouble())).thenReturn(List.of());
        when(groq.recommend(any(), any(), any()))
                .thenReturn(new GroqService.GroqResult("Inga laddare i närheten.", ""));

        mvc.perform(get("/api/stations")
                .header("X-Forwarded-For", "10.9.9.11")
                .param("lat", "59.33").param("lon", "18.06").param("carIndex", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.stations.length()").value(0))
           // Jackson serialiserar null som "sourceError": null här (ingen NON_NULL-konfig),
           // så fältet FINNS men ska vara tomt. doesNotExist() hade fällt av fel skäl.
           .andExpect(jsonPath("$.sourceError").isEmpty());
    }

    @Test
    void healthVisarKvotstatus() throws Exception {
        when(groq.isQuotaExceeded()).thenReturn(true);

        mvc.perform(get("/api/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("ok"))
           .andExpect(jsonPath("$.groq").value("quota_exceeded"));
    }

    @Test
    void chattUtanMeddelandenGerInfoSvar() throws Exception {
        mvc.perform(post("/api/chat")
                .header("X-Forwarded-For", "10.3.3.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"messages\":[]}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.reply").value("Inga meddelanden."));
    }

    // --- /api/charging-price (konsumeras av Bilresas kalkylator) ---

    @Test
    void laddprisVAljerNarmasteDCMedKandOperator() throws Exception {
        // Närmast: AC-station (hoppas över), sedan DC med okänd operatör (hoppas över),
        // sedan DC Ionity med pris i tabellen — den ska vinna trots störst avstånd
        StationDto ac        = new StationDto("P-hus AC", "Gata 1", 0.3, 59.0, 18.0, 22, 22,
                "Type 2 (AC)", "Ionity", null, null, 0, "1");
        StationDto okandDc   = new StationDto("BRF Laddaren", "Gata 2", 0.8, 59.0, 18.0, 150, 150,
                "CCS Combo 2 (DC)", "Lokal BRF", null, null, 0, "2");
        StationDto ionityDc  = new StationDto("Ionity Arlanda", "Gata 3", 2.75, 59.0, 18.0, 250, 350,
                "CCS Combo 2 (DC)", "Ionity", null, null, 0, "3");
        when(ocm.findNearby(anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(ionityDc, ac, okandDc));
        when(operatorPrices.getApproxPrice("Ionity", "Ionity Arlanda")).thenReturn("~6,96 kr/kWh");
        when(operatorPrices.parseKr("~6,96 kr/kWh")).thenReturn(6.96);
        when(operatorPrices.nationalAverageKr()).thenReturn(4.72);

        mvc.perform(get("/api/charging-price")
                .header("X-Forwarded-For", "10.5.5.5")
                .param("lat", "59.33").param("lon", "18.06"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.source").value("nearest-station"))
           .andExpect(jsonPath("$.priceKr").value(6.96))
           .andExpect(jsonPath("$.station").value("Ionity Arlanda"))
           .andExpect(jsonPath("$.operator").value("Ionity"))
           .andExpect(jsonPath("$.distanceKm").value(2.8))
           .andExpect(jsonPath("$.avgNationalKr").value(4.72));
    }

    @Test
    void laddprisErsatterOkandOperatorMedStationsnamn() throws Exception {
        // OCM:s "(Unknown Operator)"-platshållare ska inte läcka till konsument-UI:t
        StationDto lidl = new StationDto("Lidl Kungsbacka", "Gata 4", 1.1, 57.5, 12.1, 150, 150,
                "CCS Combo 2 (DC)", "(Unknown Operator)", null, null, 0, "4");
        when(ocm.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of(lidl));
        when(operatorPrices.getApproxPrice("(Unknown Operator)", "Lidl Kungsbacka")).thenReturn("~2,99 kr/kWh");
        when(operatorPrices.parseKr("~2,99 kr/kWh")).thenReturn(2.99);
        when(operatorPrices.nationalAverageKr()).thenReturn(4.75);

        mvc.perform(get("/api/charging-price")
                .header("X-Forwarded-For", "10.8.8.8")
                .param("lat", "57.5").param("lon", "12.1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.source").value("nearest-station"))
           .andExpect(jsonPath("$.operator").value("Lidl Kungsbacka"));
    }

    @Test
    void laddprisUtanKoordinaterGerRiksgenomsnitt() throws Exception {
        when(operatorPrices.nationalAverageKr()).thenReturn(4.72);

        mvc.perform(get("/api/charging-price").header("X-Forwarded-For", "10.6.6.6"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.source").value("national-average"))
           .andExpect(jsonPath("$.priceKr").value(4.72));
    }

    @Test
    void laddprisFallerTillbakaNarOCMFelar() throws Exception {
        when(ocm.findNearby(anyDouble(), anyDouble(), any()))
                .thenThrow(new RuntimeException("OCM nere"));
        when(operatorPrices.nationalAverageKr()).thenReturn(4.72);

        mvc.perform(get("/api/charging-price")
                .header("X-Forwarded-For", "10.7.7.7")
                .param("lat", "59.33").param("lon", "18.06"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.source").value("national-average"))
           .andExpect(jsonPath("$.priceKr").value(4.72));
    }

    @Test
    void chattensRateLimitGer429EfterTioAnrop() throws Exception {
        when(carSpecService.getCars()).thenReturn(CARS);
        when(groq.chat(any(), any(), any())).thenReturn("svar");

        String body = "{\"messages\":[{\"role\":\"user\",\"content\":\"hej\"}]}";
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/chat")
                    .header("X-Forwarded-For", "10.4.4.4")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
               .andExpect(status().isOk());
        }
        mvc.perform(post("/api/chat")
                .header("X-Forwarded-For", "10.4.4.4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
           .andExpect(status().isTooManyRequests());
    }
}
