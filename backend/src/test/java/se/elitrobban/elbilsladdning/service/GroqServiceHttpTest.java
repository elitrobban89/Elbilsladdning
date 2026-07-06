package se.elitrobban.elbilsladdning.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-felvägstester för GroqService: 429 med kvotspärr, trasigt JSON och
 * sektionsparsning av lyckade svar. Tjänsten pekas mot en lokal stubbserver
 * via groq.api.url — inga externa anrop. Ny tjänstinstans per test.
 */
class GroqServiceHttpTest {

    private HttpServer server;
    private GroqService service;
    private final AtomicInteger requests = new AtomicInteger();

    private volatile int status = 200;
    private volatile String body = "{}";

    private static final CarSpec CAR =
            new CarSpec("Tesla Model 3", 11, 250, List.of("type2", "ccs"), 60, 510, 550_000);

    private static final StationDto STATION = new StationDto(
            "Ionity Arlanda", "Arlandavägen 1", 1.2, 59.65, 17.93, 250, 350,
            "ccs", "Ionity", null, null, 0, "12345");

    @BeforeEach
    void setUp() throws Exception {
        requests.set(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        service = new GroqService();
        ReflectionTestUtils.setField(service, "apiKey", "test-nyckel");
        ReflectionTestUtils.setField(service, "groqUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private String chat(String fraga) {
        return service.chat(List.of(Map.of("role", "user", "content", fraga)), List.of(), null);
    }

    private static String ok(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
    }

    // --- chat ---

    @Test
    void chattPlockarSvaretUrChoices() {
        status = 200;
        body = ok("Ladda hemma sa blir det billigast.");
        assertThat(chat("var laddar jag billigast?"))
                .isEqualTo("Ladda hemma sa blir det billigast.");
        assertThat(service.isQuotaExceeded()).isFalse();
    }

    @Test
    void chatt429SatterKvotsparrOchKortsluterNastaAnrop() {
        status = 429;
        body = "{\"error\":{\"message\":\"Rate limit reached, try again in 2m59.56s\"}}";

        assertThat(chat("hej")).contains("dagsgränsen är nådd");
        assertThat(service.isQuotaExceeded()).isTrue();

        // Nästa anrop kortsluts av kvotspärren — servern anropas inte igen
        int before = requests.get();
        assertThat(chat("hej igen")).contains("dagsgränsen är nådd");
        assertThat(requests.get()).isEqualTo(before);
    }

    @Test
    void chattTrasigtJsonGerUrsaktandeSvar() {
        status = 200;
        body = "<html>inte json</html>";
        assertThat(chat("hej")).isEqualTo("Tyvärr kunde jag inte svara just nu. Försök igen!");
        assertThat(service.isQuotaExceeded()).isFalse();
    }

    // --- recommend ---

    @Test
    void rekommendationensSektionerParsas() {
        status = 200;
        body = ok("REKOMMENDATION: Ta Ionity Arlanda.\\nVISSTE DU ATT: Elmotorer har fa rorliga delar.");

        GroqService.GroqResult r = service.recommend(CAR, List.of(STATION), null);
        assertThat(r.recommendation()).isEqualTo("Ta Ionity Arlanda.");
        assertThat(r.funFact()).isEqualTo("Elmotorer har fa rorliga delar.");
    }

    @Test
    void rekommendation429GerFallbackMedNarmastaStationen() {
        status = 429;
        body = "{\"error\":{\"message\":\"try again in 30s\"}}";

        GroqService.GroqResult r = service.recommend(CAR, List.of(STATION), null);
        assertThat(r.recommendation())
                .contains("Ionity Arlanda")
                .contains("Tesla Model 3");
        assertThat(service.isQuotaExceeded()).isTrue();
    }

    @Test
    void rekommendationTrasigtSvarGerFallbackUtanCachning() {
        status = 200;
        body = "{\"oformodat\":true}";

        GroqService.GroqResult r = service.recommend(CAR, List.of(STATION), null);
        assertThat(r.recommendation()).contains("Ionity Arlanda");
    }
}
