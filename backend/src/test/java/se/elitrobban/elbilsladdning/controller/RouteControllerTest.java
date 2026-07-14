package se.elitrobban.elbilsladdning.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.RouteResponse;
import se.elitrobban.elbilsladdning.model.RouteStop;
import se.elitrobban.elbilsladdning.service.CarSpecService;
import se.elitrobban.elbilsladdning.service.RouteService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-lagertester för RouteController: bilindexvalidering och ruttplanens
 * JSON-form. Tjänsterna mockas.
 */
@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RouteService routeService;

    @MockBean
    private CarSpecService carSpecService;

    private static final CarSpec CAR =
            new CarSpec("Tesla Model 3", 11, 250, List.of("type2", "ccs"), 60, 510, 550_000);

    @Test
    void ogiltigtBilindexGer400() throws Exception {
        when(carSpecService.getCars()).thenReturn(List.of(CAR));

        mvc.perform(get("/api/route-stations")
                .param("startLat", "59.33").param("startLon", "18.06")
                .param("endLat", "57.71").param("endLon", "11.97")
                .param("carIndex", "5"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void ruttplanReturnerasMedStopp() throws Exception {
        when(carSpecService.getCars()).thenReturn(List.of(CAR));
        when(routeService.plan(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(new RouteResponse(450.0, 1, "Tesla Model 3",
                        List.of(new RouteStop(1, 225.0, null))));

        mvc.perform(get("/api/route-stations")
                .param("startLat", "59.33").param("startLon", "18.06")
                .param("endLat", "57.71").param("endLon", "11.97")
                .param("carIndex", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.totalDistanceKm").value(450.0))
           .andExpect(jsonPath("$.carName").value("Tesla Model 3"))
           .andExpect(jsonPath("$.stops[0].order").value(1))
           .andExpect(jsonPath("$.stops[0].distanceFromStartKm").value(225.0));
    }

    @Test
    void utanBilindexAnvandsGeneriskBil() throws Exception {
        // Externa konsumenter (Bilresas kalkylator) skickar rangeKm i stället för carIndex
        org.mockito.ArgumentCaptor<CarSpec> captor = org.mockito.ArgumentCaptor.forClass(CarSpec.class);
        when(routeService.plan(anyDouble(), anyDouble(), anyDouble(), anyDouble(), captor.capture()))
                .thenReturn(new RouteResponse(398.0, 1, "Generisk elbil", List.of()));

        mvc.perform(get("/api/route-stations")
                .param("startLat", "59.33").param("startLon", "18.06")
                .param("endLat", "57.71").param("endLon", "11.97")
                .param("rangeKm", "350"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carName").value("Generisk elbil"));

        org.assertj.core.api.Assertions.assertThat(captor.getValue().rangeKm()).isEqualTo(350);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().connectors())
                .containsExactly("ccs", "chademo", "type2");
    }

    @Test
    void rangeKmKlampasTillRimligtSpann() throws Exception {
        org.mockito.ArgumentCaptor<CarSpec> captor = org.mockito.ArgumentCaptor.forClass(CarSpec.class);
        when(routeService.plan(anyDouble(), anyDouble(), anyDouble(), anyDouble(), captor.capture()))
                .thenReturn(new RouteResponse(398.0, 0, "Generisk elbil", List.of()));

        mvc.perform(get("/api/route-stations")
                .param("startLat", "59.33").param("startLon", "18.06")
                .param("endLat", "57.71").param("endLon", "11.97")
                .param("rangeKm", "9999"))
           .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(captor.getValue().rangeKm()).isEqualTo(800);
    }
}
