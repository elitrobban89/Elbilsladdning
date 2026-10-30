package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;
import se.elitrobban.elbilsladdning.model.BilvideoSnapshot;
import se.elitrobban.elbilsladdning.repository.BilvideoRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fixturen är Alriks Bilars riktiga kanalflöde hämtat 2026-10-30: 15 poster, varav 7 shorts.
 *
 * @author Robert Andersson Kopler
 */
class BilvideoServiceTest {

    private static String fixtur() throws Exception {
        try (InputStream in = BilvideoServiceTest.class.getResourceAsStream("/alriks-bilar-rss-2026-10-30.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void deFemSenasteVanligaVideornaNyastForst() throws Exception {
        List<BilvideoService.Video> videor = BilvideoService.parse(fixtur());

        assertThat(videor).hasSize(BilvideoService.ANTAL_SENASTE);
        assertThat(videor.get(0).id()).isEqualTo("rcq_ny8kZrA");
        assertThat(videor.get(0).titel()).isEqualTo("Därför är Volvos kris vår kris");
        assertThat(videor.get(0).datum()).isEqualTo("2026-10-29");
        assertThat(videor.get(0).kanal()).isEqualTo("Alriks Bilar");
        assertThat(videor.get(0).utvald()).isFalse();
        assertThat(videor).extracting(BilvideoService.Video::id)
                .containsExactly("rcq_ny8kZrA", "iXEylFm3HSQ", "IKg1Ft57tL8", "4TeI_LY4vlQ", "UYPUI7jzDTc");
    }

    @Test
    void shortsSorterasBort() throws Exception {
        assertThat(BilvideoService.parse(fixtur()))
                .noneMatch(v -> v.titel().contains("#shorts"))
                .extracting(BilvideoService.Video::id)
                .doesNotContain("8nSOnPLHu1g", "0WribbH_Ba0", "qPfYXqrm3eU");
    }

    @Test
    void shortUtanTitelmarkeringKannsIgenPaLanken() {
        String xml = """
                <feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
                  <entry><yt:videoId>AAAAAAAAAAA</yt:videoId><title>Kort klipp</title>
                    <link rel="alternate" href="https://www.youtube.com/shorts/AAAAAAAAAAA"/>
                    <published>2026-10-30T11:04:29+00:00</published></entry>
                  <entry><yt:videoId>BBBBBBBBBBB</yt:videoId><title>Långt test</title>
                    <link rel="alternate" href="https://www.youtube.com/watch?v=BBBBBBBBBBB"/>
                    <published>2026-10-29T23:30:00+00:00</published></entry>
                </feed>""";
        List<BilvideoService.Video> videor = BilvideoService.parse(xml);
        assertThat(videor).extracting(BilvideoService.Video::id).containsExactly("BBBBBBBBBBB");
        // 23:30 UTC är redan nästa dag i Sverige
        assertThat(videor.get(0).datum()).isEqualTo("2026-10-30");
    }

    @Test
    void trasigtFlodeGerTomLista() {
        assertThat(BilvideoService.parse("<html>inte ett flöde</html>")).isEmpty();
        assertThat(BilvideoService.parse("")).isEmpty();
    }

    @Test
    void utvaldaKommerEfterDeSenasteOchDubbleras() {
        BilvideoService.Video senaste = new BilvideoService.Video("rcq_ny8kZrA", "Volvo", "Alriks Bilar", "2026-10-29", false);
        List<BilvideoService.Video> alla = BilvideoService.medUtvalda(List.of(senaste));

        assertThat(alla).hasSize(1 + BilvideoService.UTVALDA.size());
        assertThat(alla.get(0)).isEqualTo(senaste);
        assertThat(alla.subList(1, alla.size())).allMatch(BilvideoService.Video::utvald);
        assertThat(alla).extracting(BilvideoService.Video::id).contains("z1AENQtVddg", "G4SfUar-MVw");

        // En utvald video som redan står bland de senaste visas bara en gång.
        BilvideoService.Video sammaSomUtvald = new BilvideoService.Video("z1AENQtVddg", "Test", "Alriks Bilar", "2026-08-31", false);
        assertThat(BilvideoService.medUtvalda(List.of(sammaSomUtvald)))
                .filteredOn(v -> v.id().equals("z1AENQtVddg")).hasSize(1);
    }

    @Test
    void utvaldaHarGiltigaIdOchDatum() {
        assertThat(BilvideoService.UTVALDA).allSatisfy(v -> {
            assertThat(v.id()).matches("[A-Za-z0-9_-]{11}");
            assertThat(v.datum()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(v.utvald()).isTrue();
        });
    }

    @Test
    void flodetNereUtanReservGerBaraDeUtvalda() {
        BilvideoRepository repo = mock(BilvideoRepository.class);
        when(repo.findById(BilvideoSnapshot.ENDA_ID)).thenReturn(Optional.empty());
        BilvideoService service = new BilvideoService(repo) {
            @Override String hamta() { throw new IllegalStateException("HTTP 404"); }
        };

        BilvideoService.Videolista lista = service.senaste();
        assertThat(lista.videor()).hasSize(BilvideoService.UTVALDA.size()).allMatch(BilvideoService.Video::utvald);
        verify(repo, never()).save(any());
    }

    @Test
    void flodetNereVisarSparadLista() {
        BilvideoRepository repo = mock(BilvideoRepository.class);
        when(repo.findById(BilvideoSnapshot.ENDA_ID)).thenReturn(Optional.of(new BilvideoSnapshot(
                "[{\"id\":\"rcq_ny8kZrA\",\"titel\":\"Därför är Volvos kris vår kris\",\"kanal\":\"Alriks Bilar\","
                        + "\"datum\":\"2026-10-29\",\"utvald\":false}]", Instant.parse("2026-10-30T08:00:00Z"))));
        BilvideoService service = new BilvideoService(repo) {
            @Override String hamta() { throw new IllegalStateException("HTTP 404"); }
        };

        assertThat(service.senaste().videor().get(0).id()).isEqualTo("rcq_ny8kZrA");
    }

    @Test
    void lyckadLasningSparasSomReserv() throws Exception {
        BilvideoRepository repo = mock(BilvideoRepository.class);
        String xml = fixtur();
        BilvideoService service = new BilvideoService(repo) {
            @Override String hamta() { return xml; }
        };

        assertThat(service.senaste().videor()).hasSize(BilvideoService.ANTAL_SENASTE + BilvideoService.UTVALDA.size());
        verify(repo).save(any(BilvideoSnapshot.class));
    }
}
