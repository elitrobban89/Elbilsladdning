package se.elitrobban.elbilsladdning.scraper;

import org.junit.jupiter.api.Test;
import se.elitrobban.elbilsladdning.model.EvSalesRankEntry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testar EvSalesRankSyncService.parse() mot en realistisk fixture av elbilsvaruhuset.se:s
 * inläggs-HTML (fångad live 2026-07-20). Kritiskt: "Polestar 4" har en siffra i modellnamnet —
 * en naiv regex på plattad text ("stoppa vid första siffran") skulle blanda ihop modellnamnet
 * med antalskolumnen. parse() går via riktiga table/td-celler och ska hantera det korrekt.
 */
class EvSalesRankSyncServiceParseTest {

    // Nedkortad men strukturellt identisk kopia av den riktiga <figure class="wp-block-table">
    // som elbilsvaruhuset.se/wp-json returnerar i content.rendered
    private static final String FIXTURE = """
            <p>Vilka är de populäraste elbilarna i Sverige 2026?</p>
            <figure class="wp-block-table has-fixed-layout"><table class="has-fixed-layout"><thead><tr><th></th><th>Modell</th><th>Antal nyregistreringar jan-jun 2026</th><th>Frånpris</th></tr></thead><tbody>
            <tr><td>1</td><td><a href="#volvo-ex40">Volvo EX40</a></td><td>6 275 st</td><td>529 900 kr</td></tr>
            <tr><td>2</td><td><a href="#tesla-model-y">Tesla Model Y</a></td><td>4 862 st</td><td>499 990 kr</td></tr>
            <tr><td>3</td><td><a href="#volvo-ex30">Volvo EX30</a></td><td>3 395 st</td><td>429 000 kr</td></tr>
            <tr><td>4</td><td><a href="#vw-id7">Volkswagen ID.7</a></td><td>3 007 st</td><td>594 900 kr</td></tr>
            <tr><td>5</td><td><a href="#polestar-4">Polestar 4</a></td><td>2 903 st</td><td>619 000 kr</td></tr>
            <tr><td>6</td><td><a href="#kia-ev3">Kia EV3</a></td><td>2 337 st</td><td>429 900 kr</td></tr>
            <tr><td>7</td><td><a href="#vw-id4">Volkswagen ID.4</a></td><td>1 754 st</td><td>538 700 kr</td></tr>
            <tr><td>8</td><td><a href="#skoda-enyaq">Skoda Enyaq</a></td><td>1 630 st</td><td>599 500 kr</td></tr>
            <tr><td>9</td><td><a href="#kia-ev5">Kia EV5</a></td><td>1 566 st</td><td>553 900 kr</td></tr>
            <tr><td>10</td><td><a href="#toyota-bz4x">Toyota bZ4X</a></td><td>1 557 st</td><td>479 900 kr</td></tr>
            </tbody></table><figcaption class="wp-element-caption">Källa: <a href="https://mobilitysweden.se/statistik/databas-nyregistreringar">Mobility Sweden</a>, januari-juni 2026.</figcaption></figure>
            <h2>De 10 populäraste elbilarna, en genomgång</h2>
            <h3 id="volvo-ex40">1. Volvo EX40</h3>
            <table><tbody><tr><td>Nyregistreringar jan-jun 2026</td><td>Pris från</td></tr><tr><td>6 275 st</td><td>529 900 kr</td></tr></tbody></table>
            """;

    @Test
    void parsarAllaTioRaderIRattOrdning() {
        List<EvSalesRankEntry> rows = EvSalesRankSyncService.parse(FIXTURE);
        assertThat(rows).hasSize(10);
        assertThat(rows.stream().map(EvSalesRankEntry::getRank)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void ettanArVolvoEx40MedRattaSiffror() {
        EvSalesRankEntry first = EvSalesRankSyncService.parse(FIXTURE).get(0);
        assertThat(first.getModel()).isEqualTo("Volvo EX40");
        assertThat(first.getUnits()).isEqualTo(6275);
        assertThat(first.getPriceKr()).isEqualTo(529900);
        assertThat(first.getPeriodLabel()).isEqualTo("januari-juni 2026");
    }

    @Test
    void polestarFyraMedSiffraIModellnamnetBlandasInteIhopMedAntalet() {
        EvSalesRankEntry polestar = EvSalesRankSyncService.parse(FIXTURE).stream()
                .filter(r -> r.getRank() == 5).findFirst().orElseThrow();
        assertThat(polestar.getModel()).isEqualTo("Polestar 4");
        assertThat(polestar.getUnits()).isEqualTo(2903);
        assertThat(polestar.getPriceKr()).isEqualTo(619000);
    }

    @Test
    void idPrickSjuMedPunktIModellnamnetTolkasKorrekt() {
        EvSalesRankEntry idSeven = EvSalesRankSyncService.parse(FIXTURE).stream()
                .filter(r -> r.getRank() == 4).findFirst().orElseThrow();
        assertThat(idSeven.getModel()).isEqualTo("Volkswagen ID.7");
        assertThat(idSeven.getUnits()).isEqualTo(3007);
    }

    @Test
    void toyotaBz4xMedSiffraIModellnamnetSistPaListanTolkasKorrekt() {
        EvSalesRankEntry last = EvSalesRankSyncService.parse(FIXTURE).get(9);
        assertThat(last.getModel()).isEqualTo("Toyota bZ4X");
        assertThat(last.getUnits()).isEqualTo(1557);
        assertThat(last.getPriceKr()).isEqualTo(479900);
    }

    @Test
    void tomHtmlGerTomLista() {
        assertThat(EvSalesRankSyncService.parse("<p>inget innehåll</p>")).isEmpty();
    }

    @Test
    void radMedFörFåKolumnerHoppasÖver() {
        String html = """
                <figure class="wp-block-table"><table><tbody>
                <tr><td>1</td><td>Bara två kolumner</td></tr>
                </tbody></table></figure>
                """;
        assertThat(EvSalesRankSyncService.parse(html)).isEmpty();
    }
}
