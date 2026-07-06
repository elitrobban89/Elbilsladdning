package se.elitrobban.elbilsladdning.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import se.elitrobban.elbilsladdning.model.FavoriteStation;
import se.elitrobban.elbilsladdning.repository.FavoriteStationRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-lagertester för FavoriteController: dubblettskydd (409), ägarkontroll
 * vid borttagning (404) och svarens form. Repositoryt mockas — ingen databas.
 */
@WebMvcTest(FavoriteController.class)
class FavoriteControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private FavoriteStationRepository repo;

    private static FavoriteStation favorite(String userId, String name) {
        FavoriteStation f = new FavoriteStation();
        f.setUserId(userId);
        f.setName(name);
        f.setLat(59.33);
        f.setLon(18.06);
        f.setOperator("Ionity");
        return f;
    }

    @Test
    void favoriterListasPerAnvandare() throws Exception {
        when(repo.findByUserIdOrderByCreatedAtDesc("u1"))
                .thenReturn(List.of(favorite("u1", "Ionity Arlanda")));

        mvc.perform(get("/api/favorites/u1"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].name").value("Ionity Arlanda"))
           .andExpect(jsonPath("$[0].operator").value("Ionity"));
    }

    @Test
    void nyFavoritSparas() throws Exception {
        when(repo.existsByUserIdAndNameAndLat("u1", "Ionity Arlanda", 59.33)).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mvc.perform(post("/api/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"u1\",\"name\":\"Ionity Arlanda\",\"lat\":59.33,\"lon\":18.06}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.name").value("Ionity Arlanda"));
    }

    @Test
    void dubblettGer409OchSparasInte() throws Exception {
        when(repo.existsByUserIdAndNameAndLat("u1", "Ionity Arlanda", 59.33)).thenReturn(true);

        mvc.perform(post("/api/favorites")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"u1\",\"name\":\"Ionity Arlanda\",\"lat\":59.33,\"lon\":18.06}"))
           .andExpect(status().isConflict());
        verify(repo, never()).save(any());
    }

    @Test
    void borttagningKraverRattAnvandare() throws Exception {
        when(repo.findById(7L)).thenReturn(Optional.of(favorite("nagon-annan", "Ionity Arlanda")));

        mvc.perform(delete("/api/favorites/7").param("userId", "u1"))
           .andExpect(status().isNotFound());
        verify(repo, never()).delete(any());
    }

    @Test
    void borttagningAvEgenFavoritGer204() throws Exception {
        FavoriteStation own = favorite("u1", "Ionity Arlanda");
        when(repo.findById(7L)).thenReturn(Optional.of(own));

        mvc.perform(delete("/api/favorites/7").param("userId", "u1"))
           .andExpect(status().isNoContent());
        verify(repo).delete(own);
    }
}
