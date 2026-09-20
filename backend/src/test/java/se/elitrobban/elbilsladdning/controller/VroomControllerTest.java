package se.elitrobban.elbilsladdning.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import se.elitrobban.elbilsladdning.service.VroomNewsService;
import se.elitrobban.elbilsladdning.service.VroomTopCarsService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-lagret för Vroom-fliken. Provet finns av en konkret anledning: tjänsterna var
 * enhetstestade i botten — 17 gröna prov — men {@code /api/vroom-top-cars} hade <b>ingen
 * controller alls</b>, och det upptäcktes först när endpointen svarade 404 i drift. En grön
 * svit sa ingenting om saken, för ingenting i den gick via HTTP.
 *
 * <p>Läxan är densamma som drift-provet för ev-app.js bär: ett prov som mäter delen bevisar
 * inte att delen är ANSLUTEN. Båda vägarna prövas därför här, inte bara den som saknades.
 *
 * @author Robert Andersson Kopler
 */
@WebMvcTest({VroomTopCarsController.class, VroomNewsController.class})
class VroomControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private VroomTopCarsService topCars;
    @MockBean private VroomNewsService news;

    @Test
    void topplistanSvararPaSinAdress() throws Exception {
        when(topCars.senaste()).thenReturn(new VroomTopCarsService.Topplista(
                "augusti 2026", "Vroom", "Studio Esse", "https://youtu.be/x",
                List.of(new VroomTopCarsService.Bil(1, "EX40", "Volvo", 731))));

        mvc.perform(get("/api/vroom-top-cars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manad").value("augusti 2026"))
                .andExpect(jsonPath("$.kalla").value("Vroom"))
                .andExpect(jsonPath("$.sammanstalltAv").value("Studio Esse"))
                .andExpect(jsonPath("$.bilar[0].modell").value("EX40"))
                .andExpect(jsonPath("$.bilar[0].antal").value(731));
    }

    @Test
    void nyheternaSvararPaSinAdress() throws Exception {
        when(news.senaste()).thenReturn(new VroomNewsService.Nyheter(
                "september 2026", "Vroom", VroomNewsService.KALL_LANK,
                List.of(new VroomNewsService.Nyhet("Rubrik", "Sammanfattning",
                        "https://www.mynewsdesk.com/se/vroom/pressreleases/x",
                        "2026-09-08", "september 2026"))));

        mvc.perform(get("/api/vroom-news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manad").value("september 2026"))
                .andExpect(jsonPath("$.kalla").value("Vroom"))
                .andExpect(jsonPath("$.nyheter[0].rubrik").value("Rubrik"))
                .andExpect(jsonPath("$.nyheter[0].datum").value("2026-09-08"));
    }

    @Test
    void tommaSvarArFortfarande200OchBaraTomma() throws Exception {
        // Frontenden skiljer på "inget att visa" och "trasigt": tom lista ritar ingen flik,
        // men ett felsvar hade fått kortet att se sönder ut. Tomt är alltså 200, inte 404.
        when(topCars.senaste()).thenReturn(new VroomTopCarsService.Topplista(
                null, "Vroom", "Studio Esse", null, List.of()));
        when(news.senaste()).thenReturn(new VroomNewsService.Nyheter(
                null, "Vroom", VroomNewsService.KALL_LANK, List.of()));

        mvc.perform(get("/api/vroom-top-cars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bilar").isEmpty())
                .andExpect(jsonPath("$.kalla").value("Vroom"));
        mvc.perform(get("/api/vroom-news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nyheter").isEmpty())
                .andExpect(jsonPath("$.kalla").value("Vroom"));
    }
}
