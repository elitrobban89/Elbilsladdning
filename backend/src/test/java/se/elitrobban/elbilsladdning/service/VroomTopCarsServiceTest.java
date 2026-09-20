package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
}
