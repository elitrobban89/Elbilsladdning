package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import se.elitrobban.elbilsladdning.model.VroomTopplistaSnapshot;
import se.elitrobban.elbilsladdning.repository.VroomTopplistaRepository;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fixturen är kopierad ur det riktiga kanalflödet (hämtat 2026-09-17), inklusive em-dashen och
 * den blandade posttypen: kanalen publicerar bilnyheter och tester mellan månadslistorna, så
 * parsern måste hoppa förbi poster utan rubrikrad i stället för att ta den första bästa.
 *
 * @author Robert Andersson Kopler
 */
class VroomTopCarsServiceTest {

    private static final String LISTA = """
            Topp 25 personbilar, Augusti 2026:

            1. EX40 (Volvo) — 731 st
            2. ID.7 (Volkswagen) — 515 st
            3. EV3 (Kia) — 485 st
            4. EX30 (Volvo) — 444 st
            5. IX3 (BMW) — 430 st
            6. GLC (Mercedes-Benz) — 419 st
            7. EV5 (Kia) — 354 st
            8. EV2 (Kia) — 334 st
            9. ID.4 (Volkswagen) — 320 st
            10. BZ4X (Toyota) — 274 st
            11. ENYAQ (Skoda) — 227 st
            12. GLB (Mercedes-Benz) — 205 st

            Källa: https://vroom.nu
            """;

    private static String flode(String... beskrivningar) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<feed xmlns:media=\"http://search.yahoo.com/mrss/\" xmlns=\"http://www.w3.org/2005/Atom\">");
        int i = 0;
        for (String b : beskrivningar) {
            sb.append("<entry><title>Video ").append(++i).append("</title>")
              .append("<link rel=\"alternate\" href=\"https://www.youtube.com/watch?v=video").append(i).append("\"/>")
              .append("<media:group><media:description>")
              .append(b.replace("&", "&amp;").replace("<", "&lt;"))
              .append("</media:description></media:group></entry>");
        }
        return sb.append("</feed>").toString();
    }

    @Test
    void tioForstaRadernaLasesMedPlatsMarkeOchAntal() {
        VroomTopCarsService.Topplista t = VroomTopCarsService.parse(flode(LISTA));

        assertThat(t.bilar()).hasSize(VroomTopCarsService.TOPP_ANTAL);
        assertThat(t.bilar().get(0)).isEqualTo(new VroomTopCarsService.Bil(1, "EX40", "Volvo", 731));
        assertThat(t.bilar().get(9)).isEqualTo(new VroomTopCarsService.Bil(10, "BZ4X", "Toyota", 274));
        assertThat(t.manad()).isEqualTo("augusti 2026");
        assertThat(t.kalla()).isEqualTo("Vroom");
        assertThat(t.sammanstalltAv()).isEqualTo("Studio Esse");
        assertThat(t.lank()).isEqualTo("https://www.youtube.com/watch?v=video1");
    }

    @Test
    void posterUtanRubrikradHoppasOver() {
        // Kanalen blandar in nyheter och tester. Den första posten med en LÄSBAR lista vinner.
        VroomTopCarsService.Topplista t = VroomTopCarsService.parse(flode(
                "Låg tågvikt på Tesla Semi ställer till det. Inga siffror här.",
                "Är det här Polestar 4 SUV? Spekulationer, 1. inget (alls) — 5 st",
                LISTA));

        assertThat(t.manad()).isEqualTo("augusti 2026");
        assertThat(t.lank()).isEqualTo("https://www.youtube.com/watch?v=video3");
    }

    @Test
    void halvListaRaknasInte() {
        // Nio rader är ingen topplista — hellre ingen flik alls än en som tar slut på plats nio.
        String kort = LISTA.replaceAll("(?m)^10\\..*\\R?", "")
                           .replaceAll("(?m)^11\\..*\\R?", "")
                           .replaceAll("(?m)^12\\..*\\R?", "");
        VroomTopCarsService.Topplista t = VroomTopCarsService.parse(flode(kort));

        assertThat(t.bilar()).isEmpty();
        assertThat(t.manad()).isNull();
        assertThat(t.kalla()).isEqualTo("Vroom");   // källan står kvar även när listan saknas
    }

    @Test
    void bindestreckOchHartMellanslagFungerarOcksa() {
        // Beskrivningen skrivs för hand varje månad. Ett vanligt bindestreck i stället för
        // em-dash, eller ett hårt mellanslag i tusentalet, får inte fälla hela listan.
        String handskrivet = LISTA.replace("—", "-").replace("731 st", "1 731 st");
        VroomTopCarsService.Topplista t = VroomTopCarsService.parse(flode(handskrivet));

        assertThat(t.bilar()).hasSize(VroomTopCarsService.TOPP_ANTAL);
        assertThat(t.bilar().get(0).antal()).isEqualTo(1731);
    }

    @Test
    void manadsnamnetNormaliserasTillGemener() {
        assertThat(VroomTopCarsService.manadsEtikett("Augusti", "2026")).isEqualTo("augusti 2026");
        assertThat(VroomTopCarsService.manadsEtikett("september", "2026")).isEqualTo("september 2026");
    }

    @Test
    void modellnamnMedSiffrorOchPlusOverlever() {
        // "POLESTAR 4", "C-HR+" och "RENAULT 5" bär siffror och tecken i själva modellnamnet.
        String svara = """
                Topp 25 personbilar, September 2026:

                1. POLESTAR 4 (Polestar) — 159 st
                2. C-HR+ (Toyota) — 115 st
                3. RENAULT 5 (Renault) — 112 st
                4. EX40 (Volvo) — 731 st
                5. ID.7 (Volkswagen) — 515 st
                6. EV3 (Kia) — 485 st
                7. EX30 (Volvo) — 444 st
                8. IX3 (BMW) — 430 st
                9. GLC (Mercedes-Benz) — 419 st
                10. EV5 (Kia) — 354 st
                """;
        VroomTopCarsService.Topplista t = VroomTopCarsService.parse(flode(svara));

        assertThat(t.bilar().get(0).modell()).isEqualTo("POLESTAR 4");
        assertThat(t.bilar().get(1).modell()).isEqualTo("C-HR+");
        assertThat(t.bilar().get(2)).isEqualTo(new VroomTopCarsService.Bil(3, "RENAULT 5", "Renault", 112));
    }

    @Test
    void tomtFlodeGerTomListaUtanAttKasta() {
        assertThat(VroomTopCarsService.parse("<feed></feed>").bilar()).isEmpty();
        assertThat(VroomTopCarsService.rader("ingen lista alls")).isEmpty();
    }

    @Test
    void vaktenMarkerNarListanBytterInnebord() {
        // Rubriken i appen sager ELBILAR, och beviset for att det stammer ar att Sveriges
        // faktiska storsaljare SAKNAS: XC60, Golf och Yaris Cross finns inte pa nagon plats.
        // Borjar de dyka upp har kallan bytt fran elbilar till alla personbilar, och da ar
        // rubriken fel. Vakten faller ingenting - den ser till att fragan nar en manniska.
        assertThatCode(() -> VroomTopCarsService.varnaOmListanByttInnebord(
                VroomTopCarsService.rader(LISTA))).doesNotThrowAnyException();

        var medXc60 = VroomTopCarsService.rader(LISTA.replace("EX40 (Volvo)", "XC60 (Volvo)"));
        assertThat(medXc60).anySatisfy(b -> assertThat(b.modell()).isEqualTo("XC60"));
        assertThatCode(() -> VroomTopCarsService.varnaOmListanByttInnebord(medXc60))
                .doesNotThrowAnyException();   // varnar, faller inte
    }

    @Test
    void raderLaserPlatsenUrTextenOchInteUrOrdningen() {
        List<VroomTopCarsService.Bil> bilar = VroomTopCarsService.rader(
                "3. EX40 (Volvo) — 731 st\n1. ID.7 (Volkswagen) — 515 st");

        assertThat(bilar.get(0).plats()).isEqualTo(3);
        assertThat(bilar.get(1).plats()).isEqualTo(1);
    }

    // --- Kvartalsvideon utan "Topp 25"-lista (2026-10-10) ---------------------------------

    private static String riktigtFlode() throws Exception {
        try (InputStream in = VroomTopCarsServiceTest.class.getResourceAsStream("/studio-esse-rss-2026-10-11.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void kvartalsvideonVinnerOverForraManadensFullaLista() throws Exception {
        // Det riktiga flödet 2026-10-11: Q3-videon överst saknar antal, augustivideon längre ned
        // har dem. Förut visades augusti — en månad efter att september redovisats.
        VroomTopCarsService.Topplista t = VroomTopCarsService.parse(riktigtFlode());

        assertThat(t.manad()).isEqualTo("Q3 2026");
        assertThat(t.lank()).isEqualTo("https://www.youtube.com/watch?v=lCeDshCo5XY");
        assertThat(t.bilar()).extracting(VroomTopCarsService.Bil::modell).containsExactly(
                "EX40", "Model Y", "EX30", "EV3", "EV2", "ID.7", "EV5", "iX3", "GLC");
        assertThat(t.bilar().get(1)).isEqualTo(new VroomTopCarsService.Bil(2, "Model Y", "Tesla", null));
        assertThat(t.bilar().get(5).marke()).isEqualTo("Volkswagen");      // "VW ID.7"
        assertThat(t.bilar().get(8).marke()).isEqualTo("Mercedes-Benz");   // "Mercedes GLC"
        assertThat(t.ingress()).startsWith("I september slog elbilsandelen nytt rekord")
                .contains("Tesla Model Y blev septembers mest sålda bil");
    }

    @Test
    void namnlistaUtanPeriodRaknasInte() {
        // "Topp 5: billigaste elbilarna" i en testvideo är ingen försäljningslista.
        assertThat(VroomTopCarsService.namnlista("Billigaste elbilarna",
                "Topp 5: Kia EV2, Volvo EX30, MG MG4, Renault 5, Dacia Spring", null)).isNull();
    }

    @Test
    void namnlistaMedOkandEnordsnamnAvstar() {
        // "Polestar" ensamt är märke eller modell — vi vet inte vilket, och då hellre ingen lista.
        assertThat(VroomTopCarsService.namnlista("Q2 2026",
                "Topp 6: Volvo EX40, Polestar, Kia EV3, VW ID.7, Tesla Model Y, BMW iX3", null)).isNull();
    }

    @Test
    void fullListaVinnerFortfarandeNarDenArNyast() {
        // Reservvägen får aldrig tränga undan en riktig tabell med antal.
        VroomTopCarsService.Topplista t = VroomTopCarsService.parse(flode(
                LISTA + "\nTopp 10: Volvo EX40, Tesla Model Y, Volvo EX30, Kia EV3, Kia EV2 – Q3 2026"));

        assertThat(t.manad()).isEqualTo("augusti 2026");
        assertThat(t.bilar().get(0).antal()).isEqualTo(731);
    }

    // --- Reserven i databasen: 2026-10-12 gav YouTubes kanalflöden 404 för alla kanaler, och
    // efter uppvakningen var minnescachen tom - kortet försvann fast listan fanns dagen innan.

    /** Tjänst vars flöde antingen svarar med {@code xml} eller, om den är null, är nere. */
    private static VroomTopCarsService tjanst(VroomTopplistaRepository repo, String xml) {
        return new VroomTopCarsService(repo) {
            @Override String hamta() {
                if (xml == null) throw new IllegalStateException("HTTP 404");
                return xml;
            }
        };
    }

    @Test
    void lyckadHamtningSparasSomReserv() {
        VroomTopplistaRepository repo = mock(VroomTopplistaRepository.class);

        VroomTopCarsService.Topplista t = tjanst(repo, flode(LISTA)).senaste();

        ArgumentCaptor<VroomTopplistaSnapshot> sparad = ArgumentCaptor.forClass(VroomTopplistaSnapshot.class);
        verify(repo).save(sparad.capture());
        assertThat(sparad.getValue().getId()).isEqualTo(VroomTopplistaSnapshot.ENDA_ID);
        assertThat(sparad.getValue().getJson()).contains("\"manad\":\"augusti 2026\"", "EX40");
        assertThat(t.bilar()).hasSize(VroomTopCarsService.TOPP_ANTAL);
    }

    @Test
    void flodetNereEfterOmstartVisarDenSparadeListan() {
        // Spara via en tjänst vars flöde fungerade, läs sedan med en ny (= omstartad) tjänst.
        VroomTopplistaRepository repo = mock(VroomTopplistaRepository.class);
        ArgumentCaptor<VroomTopplistaSnapshot> sparad = ArgumentCaptor.forClass(VroomTopplistaSnapshot.class);
        VroomTopCarsService.Topplista fore = tjanst(repo, flode(LISTA)).senaste();
        verify(repo).save(sparad.capture());
        when(repo.findById(VroomTopplistaSnapshot.ENDA_ID)).thenReturn(Optional.of(sparad.getValue()));

        VroomTopCarsService.Topplista efter = tjanst(repo, null).senaste();

        assertThat(efter).isEqualTo(fore);
    }

    @Test
    void reservenProvasInteOmPaVarjeAnrop() {
        VroomTopplistaRepository repo = mock(VroomTopplistaRepository.class);
        when(repo.findById(VroomTopplistaSnapshot.ENDA_ID)).thenReturn(Optional.of(
                new VroomTopplistaSnapshot("{\"manad\":\"Q3 2026\",\"kalla\":\"Vroom\",\"bilar\":"
                        + "[{\"plats\":1,\"modell\":\"EX40\",\"marke\":\"Volvo\",\"antal\":null}]}", Instant.now())));
        VroomTopCarsService s = tjanst(repo, null);

        s.senaste();
        s.senaste();

        verify(repo, times(1)).findById(VroomTopplistaSnapshot.ENDA_ID);
    }

    @Test
    void utanReservOchUtanFlodeBlirListanTom() {
        VroomTopplistaRepository repo = mock(VroomTopplistaRepository.class);
        when(repo.findById(VroomTopplistaSnapshot.ENDA_ID)).thenReturn(Optional.empty());

        assertThat(tjanst(repo, null).senaste().bilar()).isEmpty();
    }

    @Test
    void databasfelFallerAldrigSvaret() {
        VroomTopplistaRepository repo = mock(VroomTopplistaRepository.class);
        when(repo.save(any())).thenThrow(new IllegalStateException("db nere"));
        when(repo.findById(any())).thenThrow(new IllegalStateException("db nere"));

        assertThat(tjanst(repo, flode(LISTA)).senaste().bilar()).hasSize(VroomTopCarsService.TOPP_ANTAL);
        assertThat(tjanst(repo, null).senaste().bilar()).isEmpty();
    }
}
