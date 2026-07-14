package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.RouteResponse;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tester för ruttplaneringen: haversine-avstånd, antal laddstopp
 * utifrån bilens räckvidd och val av bästa station per stopp.
 */
@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    // Stockholm och Göteborg, fågelvägen ca 398 km
    private static final double STHLM_LAT = 59.3293, STHLM_LON = 18.0686;
    private static final double GBG_LAT   = 57.7089, GBG_LON   = 11.9746;

    @Mock
    private OcmService ocmService;

    private RouteService service() {
        // Riktiga OperatorPriceService — ren logik utan beroenden
        return new RouteService(ocmService, new OperatorPriceService());
    }

    private static CarSpec bil(int rangeKm) {
        return new CarSpec("Test-EV", 11, 150, List.of("type2", "ccs"), 60, rangeKm, 400_000);
    }

    private static StationDto station(String namn, double effKw) {
        return new StationDto(namn, "Adress 1", 1.0, 58.5, 15.0, effKw, effKw,
                "ccs", "Operator", null, null, 4, "123");
    }

    @Test
    void kortResaKraverIngaStopp() {
        // Sthlm C -> Arlanda (~37 km) med 400 km räckvidd
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, 59.6498, 17.9238, bil(400));
        assertThat(r.stopsNeeded()).isZero();
        assertThat(r.stops()).isEmpty();
        verifyNoInteractions(ocmService); // inga stationer ska ens sökas
    }

    @Test
    void haversineBeraknarFagelvagen() {
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(1000));
        assertThat(r.totalDistanceKm()).isCloseTo(398.0, within(5.0));
    }

    @Test
    void antalStoppUtgarFran75ProcentAvRackvidden() {
        // 398 km med 300 km WLTP -> hopp = 225 km -> ceil(398/225) - 1 = 1 stopp
        when(ocmService.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of());
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(300));
        assertThat(r.stopsNeeded()).isEqualTo(1);
    }

    @Test
    void kortRackviddGerFlerStopp() {
        // hopp = 150 * 0,75 = 112,5 km -> ceil(398/112,5) - 1 = 3 stopp
        when(ocmService.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of());
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(150));
        assertThat(r.stopsNeeded()).isEqualTo(3);
    }

    @Test
    void valjerStationenMedHogstEffektVidVarjeStopp() {
        when(ocmService.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of(
                station("Långsam AC", 22),
                station("Ionity 350", 350),
                station("Mellansnabb", 150)));

        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(300));
        assertThat(r.stops()).hasSize(1);
        assertThat(r.stops().get(0).station().name()).isEqualTo("Ionity 350");
    }

    @Test
    void stoppUtanStationerLamnasTomtMenRaknas() {
        when(ocmService.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of());
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(300));
        assertThat(r.stopsNeeded()).isEqualTo(1);
        assertThat(r.stops()).isEmpty();
    }

    @Test
    void stoppetLaggsHalvvagsVidEttStopp() {
        when(ocmService.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of(station("Mitt", 150)));
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(300));
        assertThat(r.stops().get(0).distanceFromStartKm())
                .isCloseTo(r.totalDistanceKm() / 2, within(1.0));
    }

    @Test
    void stoppberikasMedPrisFranOperatorstabellen() {
        StationDto ionity = new StationDto("Ionity Mariestad", "Adress 1", 1.0, 58.5, 15.0, 350, 350,
                "ccs", "Ionity GmbH", null, null, 4, "123");
        when(ocmService.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of(ionity));
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(300));
        assertThat(r.stops().get(0).station().chargepricePerKwh()).isEqualTo("~6,96 kr/kWh");
    }

    @Test
    void okandOperatorLamnarPrisetTomt() {
        StationDto okand = new StationDto("BRF Laddaren", "Adress 2", 1.0, 58.5, 15.0, 150, 150,
                "ccs", "Lokal BRF", null, null, 2, "124");
        when(ocmService.findNearby(anyDouble(), anyDouble(), any())).thenReturn(List.of(okand));
        RouteResponse r = service().plan(STHLM_LAT, STHLM_LON, GBG_LAT, GBG_LON, bil(300));
        assertThat(r.stops().get(0).station().chargepricePerKwh()).isNull();
    }
}
