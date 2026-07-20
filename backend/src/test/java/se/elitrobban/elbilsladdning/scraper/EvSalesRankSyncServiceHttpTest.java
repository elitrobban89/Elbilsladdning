package se.elitrobban.elbilsladdning.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import se.elitrobban.elbilsladdning.model.EvSalesRankEntry;
import se.elitrobban.elbilsladdning.repository.EvSalesRankRepository;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Full-flödestest av syncNow(): tjänsten pekas mot en lokal stubbserver via elbilsvaruhuset.api.url
 * (samma mönster som GroqServiceHttpTest) — inga externa anrop. Repositoryt mockas eftersom testet
 * gäller HTTP-hämtning + parsning + spara-anrop, inte JPA/databasen.
 */
class EvSalesRankSyncServiceHttpTest {

    private static final String TABLE_HTML = """
            <figure class="wp-block-table"><table><thead><tr><th></th><th>Modell</th><th>Antal</th><th>Pris</th></tr></thead><tbody>
            <tr><td>1</td><td>Volvo EX40</td><td>6 275 st</td><td>529 900 kr</td></tr>
            <tr><td>2</td><td>Tesla Model Y</td><td>4 862 st</td><td>499 990 kr</td></tr>
            </tbody></table><figcaption>Källa: Mobility Sweden, januari-juni 2026.</figcaption></figure>
            """;

    private HttpServer server;
    private EvSalesRankSyncService service;
    private EvSalesRankRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile int status = 200;
    private volatile String body = "[]";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        repo = mock(EvSalesRankRepository.class);
        service = new EvSalesRankSyncService(repo);
        ReflectionTestUtils.setField(service, "apiUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void stubPostResponse(String contentHtml) throws Exception {
        body = mapper.writeValueAsString(List.of(
                Map.of("id", 21858, "content", Map.of("rendered", contentHtml), "modified", "2026-07-02T15:42:03")
        ));
    }

    @Test
    void lyckadSynkParsarOchSparar() throws Exception {
        stubPostResponse(TABLE_HTML);

        Map<String, Object> result = service.syncNow();

        assertThat(result.get("status")).isEqualTo("OK");
        assertThat(result.get("imported")).isEqualTo(2);
        assertThat(result.get("period")).isEqualTo("januari-juni 2026");

        verify(repo).deleteAll();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvSalesRankEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(repo).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).getModel()).isEqualTo("Volvo EX40");
    }

    @Test
    void tomtSvarsarrayGerFel() throws Exception {
        body = "[]";

        Map<String, Object> result = service.syncNow();

        assertThat(result.get("status")).isEqualTo("ERROR");
        verify(repo, never()).deleteAll();
        verify(repo, never()).saveAll(any());
    }

    @Test
    void innehallUtanTabellGerFelOchRorInteBefintligaRader() throws Exception {
        stubPostResponse("<p>Inget här.</p>");

        Map<String, Object> result = service.syncNow();

        assertThat(result.get("status")).isEqualTo("ERROR");
        verify(repo, never()).deleteAll();
        verify(repo, never()).saveAll(any());
    }

    @Test
    void httpFelGerFelresultatUtanKastadException() {
        status = 500;
        body = "server error";

        Map<String, Object> result = service.syncNow();

        assertThat(result.get("status")).isEqualTo("ERROR");
        verify(repo, never()).deleteAll();
    }
}
