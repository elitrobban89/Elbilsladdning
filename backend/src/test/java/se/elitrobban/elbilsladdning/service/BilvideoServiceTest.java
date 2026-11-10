package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;
import se.elitrobban.elbilsladdning.model.BilvideoSnapshot;
import se.elitrobban.elbilsladdning.repository.BilvideoRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
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
            @Override String hamtaForsaljning() { throw new IllegalStateException("HTTP 404"); }
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
            @Override String hamtaForsaljning() { throw new IllegalStateException("HTTP 404"); }
        };

        BilvideoService.Video forsta = service.senaste().videor().get(0);
        assertThat(forsta.id()).isEqualTo("rcq_ny8kZrA");
        // Reserven sparades före 2026-11-10 och saknar försäljningsflaggan: den ska läsas som false.
        assertThat(forsta.forsaljning()).isFalse();
    }

    // ---------- Månadens försäljningsvideor ur Studio Esses flöde (2026-11-10) ----------

    private static String studioEsse(String datum) throws Exception {
        try (InputStream in = BilvideoServiceTest.class.getResourceAsStream("/studio-esse-rss-" + datum + ".xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void oktobersForsaljningsvideorHittasIRiktigaFlodet() throws Exception {
        List<BilvideoService.Video> videor = BilvideoService.parseForsaljning(studioEsse("2026-11-10"), LocalDate.of(2026, 11, 10));

        assertThat(videor).extracting(BilvideoService.Video::id).containsExactly("3dHcq44U1q8", "XqZfWbcOSco");
        assertThat(videor.get(0).titel()).isEqualTo("Nu får Volvo lugna ner sig – elbilsförsäljningen oktober 2026");
        assertThat(videor.get(0).datum()).isEqualTo("2026-11-04");
        assertThat(videor).allSatisfy(v -> {
            assertThat(v.kanal()).isEqualTo("Studio Esse");
            assertThat(v.forsaljning()).isTrue();
            assertThat(v.utvald()).isFalse();
        });
    }

    @Test
    void kvartalsvideonHittasOchProvkorningarInte() throws Exception {
        // Flödet 2026-10-11: Q3-videon är statistik, "Sveriges mest sålda pickup" är en provkörning.
        assertThat(BilvideoService.parseForsaljning(studioEsse("2026-10-11"), LocalDate.of(2026, 10, 11)))
                .extracting(BilvideoService.Video::id).containsExactly("lCeDshCo5XY");
        assertThat(BilvideoService.FORSALJNING.matcher("Sveriges mest sålda pickup blir elektrifierad (och POV-körs)").find()).isFalse();
        assertThat(BilvideoService.FORSALJNING.matcher("Kia EV 5 – bäst är billigast").find()).isFalse();
        assertThat(BilvideoService.FORSALJNING.matcher("Rekord för elbilar! Alla bilar sålda i Sverige Q3 2026, från etta till nolla").find()).isTrue();
    }

    @Test
    void forsaljningsvideonFallerBortNarDenBlivitGammal() throws Exception {
        String xml = studioEsse("2026-11-10");
        // 2026-11-04 + 40 dagar = 2026-12-14: dagen efter står den inte kvar.
        assertThat(BilvideoService.parseForsaljning(xml, LocalDate.of(2026, 12, 14))).extracting(BilvideoService.Video::id).contains("3dHcq44U1q8");
        assertThat(BilvideoService.parseForsaljning(xml, LocalDate.of(2026, 12, 15))).extracting(BilvideoService.Video::id).doesNotContain("3dHcq44U1q8");
    }

    @Test
    void forsaljningsvideornaKommerEfterDeSenasteOchForeDeUtvalda() throws Exception {
        String alriks = fixtur();
        String esse = studioEsse("2026-11-10");
        BilvideoService service = new BilvideoService(mock(BilvideoRepository.class)) {
            @Override String hamta() { return alriks; }
            @Override String hamtaForsaljning() { return esse; }
        };

        List<BilvideoService.Video> videor = service.senaste().videor();
        int n = BilvideoService.ANTAL_SENASTE;
        assertThat(videor.subList(0, n)).noneMatch(BilvideoService.Video::forsaljning).noneMatch(BilvideoService.Video::utvald);
        // Fixturen är från 2026-11-10 men klockan är verklig: efter 2026-12-12 har båda åldrats ut.
        List<BilvideoService.Video> forsaljning = videor.stream().filter(BilvideoService.Video::forsaljning).toList();
        assertThat(forsaljning).hasSizeLessThanOrEqualTo(BilvideoService.ANTAL_FORSALJNING);
        assertThat(videor.subList(n, n + forsaljning.size())).isEqualTo(forsaljning);
        assertThat(videor.subList(n + forsaljning.size(), videor.size())).allMatch(BilvideoService.Video::utvald);
    }

    @Test
    void forsaljningsflodetNereGerKortetUtanDem() throws Exception {
        String alriks = fixtur();
        BilvideoService service = new BilvideoService(mock(BilvideoRepository.class)) {
            @Override String hamta() { return alriks; }
            @Override String hamtaForsaljning() { throw new IllegalStateException("HTTP 500"); }
        };

        assertThat(service.senaste().videor())
                .hasSize(BilvideoService.ANTAL_SENASTE + BilvideoService.UTVALDA.size())
                .noneMatch(BilvideoService.Video::forsaljning);
    }

    @Test
    void ingenForsaljningsvideoLiggerKvarSomUtvald() {
        // De två oktobervideorna stod för hand i UTVALDA några timmar 2026-11-10. Nu kommer de ur
        // flödet, och en utvald dubblett hade legat kvar i månader efter att den blivit gammal.
        assertThat(BilvideoService.UTVALDA).noneMatch(v -> BilvideoService.FORSALJNING.matcher(v.titel()).find());
    }

    @Test
    void lyckadLasningSparasSomReserv() throws Exception {
        BilvideoRepository repo = mock(BilvideoRepository.class);
        String xml = fixtur();
        BilvideoService service = new BilvideoService(repo) {
            @Override String hamta() { return xml; }
            @Override String hamtaForsaljning() { throw new IllegalStateException("HTTP 404"); }
        };

        assertThat(service.senaste().videor()).hasSize(BilvideoService.ANTAL_SENASTE + BilvideoService.UTVALDA.size());
        verify(repo).save(any(BilvideoSnapshot.class));
    }
}
