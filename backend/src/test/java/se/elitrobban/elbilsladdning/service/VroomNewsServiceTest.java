package se.elitrobban.elbilsladdning.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provar VroomNewsService mot en fixtur som är kopierad rakt ur det riktiga flödet
 * (hämtat 2026-09-17 från {@code mynewsdesk.com/se/rss/current_news/51204}), inte ur en
 * egen föreställning om hur RSS ser ut. Tre detaljer i den riktiga datan styr koden:
 *
 * <ul>
 *   <li>{@code link} bär fyra utm-parametrar som inte hör hemma i en klickbar länk</li>
 *   <li>{@code pubDate} är RFC 1123 med svensk offset (+0200)</li>
 *   <li>flödet blandar månadsrapporter med analysartiklar, så "senaste månaden" kan
 *       innehålla allt från en rad till fem</li>
 * </ul>
 *
 * @author Robert Andersson Kopler
 */
class VroomNewsServiceTest {

    private static final String FIXTUR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <channel>
              <title>vroom Stockholm AB - Aktuellt</title>
              <item>
                <title>Begagnatmarknaden möter nya utmaningar</title>
                <pubDate>Tue, 08 Sep 2026 07:00:00 +0200</pubDate>
                <description>Bilhandelns försäljning av begagnade personbilar upp till tio år minskade med 12 % i augusti.</description>
                <link>https://www.mynewsdesk.com/se/vroom/pressreleases/begagnatmarknaden-3464880?utm_source=rss&amp;utm_medium=rss</link>
                <dc:creator>vroom Stockholm AB</dc:creator>
              </item>
              <item>
                <title>Bilmarknaden augusti 2026</title>
                <pubDate>Tue, 01 Sep 2026 18:01:46 +0200</pubDate>
                <description>Elbilar ökade med 50 % och stod för 46 % av nyregistreringarna.</description>
                <link>https://www.mynewsdesk.com/se/vroom/pressreleases/bilmarknaden-augusti-3422741?utm_source=rss</link>
              </item>
              <item>
                <title>Bilexporten fortsätter ned</title>
                <pubDate>Thu, 27 Aug 2026 07:00:00 +0200</pubDate>
                <description>Svensk bilexport fortsätter nedåt.</description>
                <link>https://www.mynewsdesk.com/se/vroom/pressreleases/bilexporten-3462455</link>
              </item>
              <item>
                <title>Bilmarknaden juli 2026</title>
                <pubDate>Tue, 04 Aug 2026 16:32:38 +0200</pubDate>
                <description>Nära sju av tio nya personbilar var laddbara.</description>
                <link>https://www.mynewsdesk.com/se/vroom/pressreleases/bilmarknaden-juli-3422742</link>
              </item>
              <item>
                <title>Rad utan datum som inte går att placera</title>
                <description>Utan pubDate vet vi inte vilken månad raden hör till.</description>
                <link>https://www.mynewsdesk.com/se/vroom/pressreleases/utan-datum</link>
              </item>
            </channel>
            </rss>
            """;

    @Test
    void parseLaserRubrikDatumOchSammanfattning() {
        List<VroomNewsService.Nyhet> rader = VroomNewsService.parse(FIXTUR);

        assertThat(rader).hasSize(4);   // raden utan pubDate är bortsorterad
        assertThat(rader.get(0).rubrik()).isEqualTo("Begagnatmarknaden möter nya utmaningar");
        assertThat(rader.get(0).datum()).isEqualTo("2026-09-08");
        assertThat(rader.get(0).manad()).isEqualTo("september 2026");
        assertThat(rader.get(0).sammanfattning()).startsWith("Bilhandelns försäljning");
    }

    @Test
    void raderSorterasNyastForst() {
        List<VroomNewsService.Nyhet> rader = VroomNewsService.parse(FIXTUR);
        assertThat(rader).extracting(VroomNewsService.Nyhet::datum)
                .containsExactly("2026-09-08", "2026-09-01", "2026-08-27", "2026-08-04");
    }

    @Test
    void utmParametrarnaFoljerInteMedTillLanken() {
        // Spårningen hör till flödet, inte till länken en läsare ska klicka på.
        List<VroomNewsService.Nyhet> rader = VroomNewsService.parse(FIXTUR);
        assertThat(rader).extracting(VroomNewsService.Nyhet::lank).allSatisfy(l ->
                assertThat(l).doesNotContain("utm_").startsWith("https://www.mynewsdesk.com/se/vroom/"));
    }

    @Test
    void senasteManadenValjsUrFlodetOchInteUrKalendern() {
        // Två poster i september, två i augusti. September är nyast men når inte MINSTA_ANTAL,
        // så listan fylls på bakåt — och etiketten blir ett spann, inte bara "september".
        VroomNewsService.Nyheter n = VroomNewsService.gruppera(VroomNewsService.parse(FIXTUR));

        assertThat(n.nyheter()).hasSize(VroomNewsService.MINSTA_ANTAL);
        assertThat(n.manad()).isEqualTo("augusti–september 2026");
        assertThat(n.kalla()).isEqualTo("Vroom");
        assertThat(n.nyheter().get(0).datum()).isEqualTo("2026-09-08");
    }

    @Test
    void enManadSomRackerTillStarEnsam() {
        String tre = FIXTUR.replace("Thu, 27 Aug 2026 07:00:00 +0200", "Thu, 24 Sep 2026 07:00:00 +0200");
        VroomNewsService.Nyheter n = VroomNewsService.gruppera(VroomNewsService.parse(tre));

        assertThat(n.manad()).isEqualTo("september 2026");
        assertThat(n.nyheter()).hasSize(3);
        assertThat(n.nyheter()).allSatisfy(r -> assertThat(r.manad()).isEqualTo("september 2026"));
    }

    @Test
    void karusellenTarAldrigFlerAnTaket() {
        StringBuilder många = new StringBuilder();
        for (int dag = 1; dag <= 12; dag++)
            många.append("<item><title>Rad ").append(dag).append("</title><pubDate>")
                 .append(String.format("Tue, %02d Sep 2026 07:00:00 +0200", dag))
                 .append("</pubDate><description>x</description><link>https://x.se/a</link></item>");
        VroomNewsService.Nyheter n = VroomNewsService.gruppera(
                VroomNewsService.parse("<rss><channel>" + många + "</channel></rss>"));

        assertThat(n.nyheter()).hasSize(VroomNewsService.HOGSTA_ANTAL);
        assertThat(n.manad()).isEqualTo("september 2026");
    }

    @Test
    void felVeckodagFarInteTaBortEnPost() {
        // RFC_1123_DATE_TIME validerar veckodagen och KASTAR när den inte stämmer. Den 8 sep
        // 2026 är en tisdag; står det "Mon" ska posten ändå med, för veckodagen bär ingen
        // information vi använder — och en tyst bortsorterad nyhet ser ut precis som ingen nyhet.
        String felDag = FIXTUR.replace("Tue, 08 Sep 2026", "Mon, 08 Sep 2026");
        List<VroomNewsService.Nyhet> rader = VroomNewsService.parse(felDag);

        assertThat(rader).hasSize(4);
        assertThat(rader.get(0).datum()).isEqualTo("2026-09-08");
    }

    @Test
    void tomtFlodeGerTomListaMenBehallerKallan() {
        // Frontenden ritar ingen flik på en tom lista — men källfälten ska ändå vara ifyllda,
        // så att ett tomt svar inte läser som ett trasigt svar.
        VroomNewsService.Nyheter n = VroomNewsService.gruppera(List.of());

        assertThat(n.nyheter()).isEmpty();
        assertThat(n.manad()).isNull();
        assertThat(n.kalla()).isEqualTo("Vroom");
        assertThat(n.kallLank()).isEqualTo(VroomNewsService.KALL_LANK);
    }

    @Test
    void manadsspannOverArsskiftetBehallerBadaAren() {
        String nyar = FIXTUR
                .replace("Tue, 08 Sep 2026 07:00:00 +0200", "Fri, 02 Jan 2027 07:00:00 +0200")
                .replace("Tue, 01 Sep 2026 18:01:46 +0200", "Mon, 21 Dec 2026 18:01:46 +0200")
                .replace("Thu, 27 Aug 2026 07:00:00 +0200", "Tue, 15 Dec 2026 07:00:00 +0200");
        VroomNewsService.Nyheter n = VroomNewsService.gruppera(VroomNewsService.parse(nyar));

        assertThat(n.manad()).isEqualTo("december 2026–januari 2027");
    }
}
