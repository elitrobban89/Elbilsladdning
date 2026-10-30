package se.elitrobban.elbilsladdning.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import se.elitrobban.elbilsladdning.service.BilvideoService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Att {@code /api/bilvideor} FINNS — samma läxa som {@link VroomControllerTest}: gröna prov på
 * tjänsten bevisar inte att någon controller kopplar den till en adress.
 *
 * @author Robert Andersson Kopler
 */
@WebMvcTest(BilvideoController.class)
class BilvideoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private BilvideoService service;

    @Test
    void videornaSvararPaSinAdress() throws Exception {
        when(service.senaste()).thenReturn(new BilvideoService.Videolista("Alriks Bilar",
                "https://www.youtube.com/@alriksbilar",
                List.of(new BilvideoService.Video("rcq_ny8kZrA", "Därför är Volvos kris vår kris",
                        "Alriks Bilar", "2026-10-29", false))));

        mvc.perform(get("/api/bilvideor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kanal").value("Alriks Bilar"))
                .andExpect(jsonPath("$.videor[0].id").value("rcq_ny8kZrA"))
                .andExpect(jsonPath("$.videor[0].datum").value("2026-10-29"))
                .andExpect(jsonPath("$.videor[0].utvald").value(false));
    }

    @Test
    void tomListaArFortfarande200() throws Exception {
        when(service.senaste()).thenReturn(new BilvideoService.Videolista("Alriks Bilar",
                "https://www.youtube.com/@alriksbilar", List.of()));

        mvc.perform(get("/api/bilvideor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.videor").isEmpty());
    }
}
