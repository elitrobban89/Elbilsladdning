package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Carlas Elbilsindex ur pressrummets RSS (2026-09-27). Fixturen är det RIKTIGA flödet som det
 * såg ut 2026-09-27 — inte en handgjord kopia, som bara hade burit min egen gissning om formatet.
 *
 * @author Robert Andersson Kopler
 */
class CarlaIndexServiceTest {

    private static String riktigtFlode() throws Exception {
        try (InputStream in = CarlaIndexServiceTest.class.getResourceAsStream("/carla-rss-2026-09-27.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void nyastaIndexetLasesUrDetRiktigaFlodet() throws Exception {
        CarlaIndexService.Index i = CarlaIndexService.parse(riktigtFlode());
        assertThat(i).isNotNull();
        assertThat(i.manad()).isEqualTo("augusti 2026");
        assertThat(i.rubrik()).startsWith("Tesla och Volvo står för var tredje såld begagnad elbil");
        assertThat(i.antal()).isEqualTo(7246);
        assertThat(i.datum()).isEqualTo("2026-09-03");
        assertThat(i.lank()).contains("cision.com");
    }

    @Test
    void bolagsnyheterRaknasInte() throws Exception {
        // flödets nyaste poster (OMODA-avtalet 09-24, tillväxten 09-11) är INTE index - de ska hoppas över
        CarlaIndexService.Index i = CarlaIndexService.parse(riktigtFlode());
        assertThat(i.rubrik()).doesNotContain("OMODA").doesNotContain("växer kraftigt");
    }

    @Test
    void siffrornaLasesIAllaTreFormuleringarna() {
        String xml = """
            <rss><channel>
            <item><title>Carlas Elbilsindex maj 2026: Rekord</title><pubDate>Mon, 08 Jun 2026 14:10:14 GMT</pubDate>
            <description>Under maj registrerades 9 673 försäljningar av begagnade elbilar i Sverige.</description></item>
            </channel></rss>""";
        assertThat(CarlaIndexService.parse(xml).antal()).isEqualTo(9673);

        String juli = """
            <rss><channel>
            <item><title>Carlas Elbilsindex juli 2026: Fler sålda</title><pubDate>Wed, 05 Aug 2026 14:10:34 GMT</pubDate>
            <description>I juli såldes 8 260 begagnade elbilar. Samtidigt steg medianpriset till 349 900 kronor.</description></item>
            </channel></rss>""";
        CarlaIndexService.Index j = CarlaIndexService.parse(juli);
        assertThat(j.antal()).isEqualTo(8260);
        assertThat(j.medianprisKr()).isEqualTo(349900);
    }

    @Test
    void ettFlodeUtanIndexGerNull() {
        String xml = "<rss><channel><item><title>Carla lanserar serviceavtal</title>"
                + "<pubDate>Mon, 25 May 2026 06:09:11 GMT</pubDate></item></channel></rss>";
        assertThat(CarlaIndexService.parse(xml)).isNull();
    }
}
