package se.elitrobban.elbilsladdning.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import se.elitrobban.elbilsladdning.service.CarSpecService;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;
import se.elitrobban.elbilsladdning.model.StationResponse;
import se.elitrobban.elbilsladdning.service.ApiNinjasService;
import se.elitrobban.elbilsladdning.service.ChargepriceService;
import se.elitrobban.elbilsladdning.service.GroqService;
import se.elitrobban.elbilsladdning.service.NobilService;
import se.elitrobban.elbilsladdning.service.OcmService;
import se.elitrobban.elbilsladdning.service.OperatorPriceService;

import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ChargingController {

    private static final Logger log = LoggerFactory.getLogger(ChargingController.class);

    private static final int  CHAT_RATE_LIMIT     = 10;
    private static final long WINDOW_MS            = 60_000L;
    private static final int  STATIONS_RATE_LIMIT  = 10;
    private static final long STATIONS_WINDOW_MS   = 3_600_000L;
    private static final int  PRICE_RATE_LIMIT     = 30;

    // Andra bältet. HTTP-klienterna har egna tidsgränser (HttpTimeouts), men taket här
    // gäller ÄVEN om en källa hänger någon annanstans än i läsningen — och det är
    // svarstiden mot användaren som räknas, inte var i anropet det tog stopp.
    private static final long KALLA_TAK_S = 12;

    // Generic DC probe car for /charging-price — accepts every connector type so
    // no station is filtered out on car capabilities
    private static final CarSpec PRICE_PROBE_CAR =
            new CarSpec("prissond", 22, 400, List.of("ccs", "chademo", "type2"), 0, 0, 0);

    // Virtual threads: cheap for I/O-bound parallel HTTP calls (Java 21+)
    private static final ExecutorService IO_POOL = Executors.newVirtualThreadPerTaskExecutor();

    private final ConcurrentHashMap<String, Deque<Long>> chatTimestamps     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> stationTimestamps  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> priceTimestamps    = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    private final OcmService           ocm;
    private final GroqService          groq;
    private final ChargepriceService   chargeprice;
    private final ApiNinjasService     apiNinjas;
    private final OperatorPriceService operatorPrices;
    private final NobilService         nobil;
    private final CarSpecService       carSpecService;

    public ChargingController(OcmService ocm, GroqService groq, ChargepriceService chargeprice,
                              ApiNinjasService apiNinjas, OperatorPriceService operatorPrices,
                              NobilService nobil, CarSpecService carSpecService) {
        this.ocm            = ocm;
        this.groq           = groq;
        this.chargeprice    = chargeprice;
        this.apiNinjas      = apiNinjas;
        this.operatorPrices = operatorPrices;
        this.nobil          = nobil;
        this.carSpecService = carSpecService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", "ok");
        if (groq.isQuotaExceeded()) {
            result.put("groq", "quota_exceeded");
        }
        return result;
    }

    @Scheduled(fixedRate = 3_600_000L)
    public void cleanRateLimitMaps() {
        long now = System.currentTimeMillis();
        chatTimestamps.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                e.getValue().removeIf(t -> t < now - WINDOW_MS);
                return e.getValue().isEmpty();
            }
        });
        stationTimestamps.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                e.getValue().removeIf(t -> t < now - STATIONS_WINDOW_MS);
                return e.getValue().isEmpty();
            }
        });
        priceTimestamps.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                e.getValue().removeIf(t -> t < now - STATIONS_WINDOW_MS);
                return e.getValue().isEmpty();
            }
        });
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, Object> req, HttpServletRequest httpReq) {
        String ip = httpReq.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
        String clientIp = ip.split(",")[0].trim();

        long now = System.currentTimeMillis();
        Deque<Long> times = chatTimestamps.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) times.pollFirst();
            if (times.size() >= CHAT_RATE_LIMIT)
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "För många frågor — vänta lite och försök igen.");
            times.addLast(now);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) req.get("messages");
        if (messages == null || messages.isEmpty()) return Map.of("reply", "Inga meddelanden.");
        String context = (String) req.get("context");
        return Map.of("reply", groq.chat(messages, carSpecService.getCars(), context));
    }

    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    public ResponseEntity<StreamingResponseBody> chatStream(@RequestBody Map<String, Object> req, HttpServletRequest httpReq) {
        String ip = httpReq.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
        String clientIp = ip.split(",")[0].trim();
        long now = System.currentTimeMillis();
        Deque<Long> times = chatTimestamps.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) times.pollFirst();
            if (times.size() >= CHAT_RATE_LIMIT)
                return ResponseEntity.status(429).build();
            times.addLast(now);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) req.get("messages");
        if (messages == null || messages.isEmpty())
            return ResponseEntity.badRequest().build();
        String context = (String) req.get("context");

        StreamingResponseBody body = outputStream -> {
            try (InputStream is = groq.chatStream(messages, carSpecService.getCars(), context);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ")) continue;
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode node = mapper.readTree(data);
                        String token = node.at("/choices/0/delta/content").asText("");
                        if (!token.isEmpty()) {
                            outputStream.write(("data: " + mapper.writeValueAsString(token) + "\n\n").getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                outputStream.write(("data: " + mapper.writeValueAsString("[ERR]" + e.getMessage()) + "\n\n").getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            }
            outputStream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };

        return ResponseEntity.ok()
                .header("Content-Type", "text/event-stream; charset=UTF-8")
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    @GetMapping("/cars")
    public List<Map<String, Object>> cars() {
        return carSpecService.getCars().stream().map(c -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("name",       c.name());
            m.put("maxAcKw",    c.maxAcKw());
            m.put("maxDcKw",    c.maxDcKw());
            m.put("connectors", c.connectors());
            m.put("batteryKwh", c.batteryKwh());
            m.put("rangeKm",    c.rangeKm());
            m.put("priceKr",    c.priceKr());
            return m;
        }).toList();
    }

    @GetMapping("/stations")
    public ResponseEntity<?> stations(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam int carIndex,
            @RequestParam(defaultValue = "speed") String sort,
            @RequestParam(defaultValue = "") String city,
            HttpServletRequest httpReq) {

        String ip = httpReq.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
        String clientIp = ip.split(",")[0].trim();
        long now = System.currentTimeMillis();
        Deque<Long> times = stationTimestamps.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > STATIONS_WINDOW_MS) times.pollFirst();
            if (times.size() >= STATIONS_RATE_LIMIT)
                return ResponseEntity.status(429).body(Map.of("error", "För många förfrågningar. Försök igen om en stund."));
            times.addLast(now);
        }

        List<CarSpec> cars = carSpecService.getCars();
        if (carIndex < 0 || carIndex >= cars.size())
            return ResponseEntity.badRequest().body(Map.of("error", "Ogiltigt bilindex: " + carIndex));

        CarSpec car = cars.get(carIndex);

        // Step 1: OCM + NOBIL in parallel — independent data sources
        var ocmFuture   = CompletableFuture.supplyAsync(() -> ocm.findNearby(lat, lon, car), IO_POOL);
        var nobilFuture = CompletableFuture.supplyAsync(() -> nobil.getStations(lat, lon), IO_POOL);

        // De två källorna bär OLIKA VIKT och måste därför fångas var för sig. OCM är
        // stationslistan; NOBIL bidrar bara med antalet kontakter per station. Med ett
        // gemensamt catch tömde ett NOBIL-fel hela listan — användaren fick noll stationer
        // och HTTP 200, alltså ett svar som inte gick att skilja från "det finns inga
        // laddare här". Uppmätt 2026-08-30: identiska anrop gav växelvis 5 och 0 stationer.
        //
        // Båda grenarna LOGGAR. Den tysta catch-grenen var det som gjorde felet omöjligt att
        // hitta i Render-loggen: tjänsten såg frisk ut hela vägen.
        List<StationDto> allStations;
        try {
            allStations = ocmFuture.get(KALLA_TAK_S, TimeUnit.SECONDS);
        } catch (Exception e) {
            ocmFuture.cancel(true);
            log.warn("OCM gav inga stationer för lat={} lon={} ({}): {}",
                     lat, lon, e.getClass().getSimpleName(), e.getMessage());
            allStations = List.of();
        }

        List<NobilService.NobilStation> nobilStations;
        try {
            nobilStations = nobilFuture.get(KALLA_TAK_S, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Kostar bara kontakträkningen — stationerna nedan står kvar.
            nobilFuture.cancel(true);
            log.warn("NOBIL svarade inte ({}): {} — kontaktantalet faller tillbaka på OCM:s",
                     e.getClass().getSimpleName(), e.getMessage());
            nobilStations = List.of();
        }

        final List<NobilService.NobilStation> nobilResult = nobilStations;
        List<StationDto> top5 = sorted(allStations, sort).stream().limit(5).toList();

        // Step 2: Price enrichment for all 5 stations in parallel
        List<CompletableFuture<StationDto>> priceFutures = top5.stream().map(s ->
            CompletableFuture.supplyAsync(() -> {
                // 1. Chargeprice (live, OCM-ID first, then network name)
                String price = chargeprice.getPricePerKwh(s, car);

                // 2. API Ninjas (live, free-text) — only if Chargeprice has nothing
                if (price == null && apiNinjas.isEnabled() && !city.isBlank())
                    price = apiNinjas.getPricing(city, s.lat(), s.lon());

                // 3. Static operator table — match on operator name, then station name
                if (price == null)
                    price = operatorPrices.getApproxPrice(s.operator(), s.name());

                // 4. NOBIL connector count within 150 m; fall back to OCM count
                int connCount = nobilResult.stream()
                        .filter(n -> NobilService.distanceKm(s.lat(), s.lon(), n.lat(), n.lon()) < 0.15)
                        .mapToInt(NobilService.NobilStation::connectorCount)
                        .max()
                        .orElse(s.connectorCount());

                String finalPrice = price != null ? price : s.chargepricePerKwh();
                return new StationDto(s.name(), s.address(), s.distanceKm(),
                                      s.lat(), s.lon(), s.maxEffKw(), s.stationKw(),
                                      s.connectorType(), s.operator(), s.usageCost(),
                                      finalPrice, connCount, s.ocmId());
            }, IO_POOL)
        ).toList();

        List<StationDto> stations = priceFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Step 3: Groq — needs enriched station + price data, runs last
        var groqResult = groq.recommend(car, stations, buildCostComparison(car));

        return ResponseEntity.ok(new StationResponse(car.name(), stations, groqResult.recommendation(), groqResult.funFact(), buildCarFact(car)));
    }

    /**
     * Fast-charge price for external consumers (Bilresa's fuel cost calculator).
     * With lat/lon: price of the nearest DC station whose operator is in the price
     * table. Without coordinates, or when nothing nearby matches: national average
     * across the operator table. Always includes avgNationalKr for fallback display.
     */
    @GetMapping("/charging-price")
    public ResponseEntity<?> chargingPrice(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon,
            HttpServletRequest httpReq) {

        String ip = httpReq.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
        String clientIp = ip.split(",")[0].trim();
        long now = System.currentTimeMillis();
        Deque<Long> times = priceTimestamps.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() > STATIONS_WINDOW_MS) times.pollFirst();
            if (times.size() >= PRICE_RATE_LIMIT)
                return ResponseEntity.status(429).body(Map.of("error", "För många förfrågningar. Försök igen om en stund."));
            times.addLast(now);
        }

        double avgNational = operatorPrices.nationalAverageKr();

        if (lat != null && lon != null) {
            List<StationDto> stations;
            try {
                stations = ocm.findNearby(lat, lon, PRICE_PROBE_CAR);
            } catch (Exception e) {
                stations = List.of();
            }
            List<StationDto> byDistance = stations.stream()
                    .sorted(Comparator.comparingDouble(StationDto::distanceKm))
                    .toList();
            for (StationDto s : byDistance) {
                if (!s.connectorType().contains("DC")) continue;
                String label = operatorPrices.getApproxPrice(s.operator(), s.name());
                if (label == null) continue;   // operator not in the price table
                Double kr = operatorPrices.parseKr(label);
                if (kr == null) continue;      // free charging — not a trip cost basis
                // OCM's "(Unknown Operator)" placeholder reads badly in consumer UIs —
                // the station name (which carried the price match) works better there
                String operator = s.operator();
                if (operator == null || operator.isBlank() || operator.contains("Unknown"))
                    operator = s.name();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("source", "nearest-station");
                out.put("priceKr", kr);
                out.put("priceLabel", label);
                out.put("station", s.name());
                out.put("operator", operator);
                out.put("distanceKm", Math.round(s.distanceKm() * 10) / 10.0);
                out.put("maxKw", Math.round(s.maxEffKw()));
                out.put("avgNationalKr", avgNational);
                return ResponseEntity.ok(out);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "national-average");
        out.put("priceKr", avgNational);
        out.put("avgNationalKr", avgNational);
        return ResponseEntity.ok(out);
    }

    private String buildCostComparison(CarSpec selected) {
        double selCpm = costPerMil(selected);
        if (selCpm <= 0) return null;

        var others = carSpecService.getCars().stream()
                .filter(c -> !c.name().equals(selected.name()) && c.rangeKm() > 0)
                .sorted(Comparator.comparingDouble(this::costPerMil))
                .toList();

        CarSpec cheapest  = others.get(0);
        CarSpec priciest  = others.get(others.size() - 1);
        CarSpec similar   = others.stream()
                .min(Comparator.comparingDouble(c -> Math.abs(costPerMil(c) - selCpm)))
                .orElse(null);

        return String.format(
            "%s: %.1f kr/mil. Billigast bland alla bilar: %s (%.1f kr/mil). " +
            "Dyrast: %s (%.1f kr/mil). Närmast i kostnad: %s (%.1f kr/mil).",
            selected.name(), selCpm,
            cheapest.name(), costPerMil(cheapest),
            priciest.name(), costPerMil(priciest),
            similar != null ? similar.name() : "-",
            similar != null ? costPerMil(similar) : 0.0);
    }

    private double costPerMil(CarSpec car) {
        if (car.rangeKm() <= 0) return Double.MAX_VALUE;
        return (car.batteryKwh() * 2.5) / (car.rangeKm() * 0.85 / 10.0);
    }

    private List<StationDto> sorted(List<StationDto> list, String sort) {
        Comparator<StationDto> cmp = switch (sort) {
            case "distance" -> Comparator.comparingDouble(StationDto::distanceKm);
            case "price"    -> Comparator.comparingDouble(s -> extractPrice(s.bestPrice()));
            default         -> Comparator.comparingDouble((StationDto s) -> -s.maxEffKw());
        };
        return list.stream().sorted(cmp).toList();
    }

    private String buildCarFact(CarSpec car) {
        if (car.priceKr() <= 0 || car.rangeKm() <= 0) return null;

        var ranked = carSpecService.getCars().stream()
                .filter(c -> c.priceKr() > 0 && c.rangeKm() > 0)
                .sorted(Comparator.comparingDouble(c -> -(c.rangeKm() * 100_000.0 / c.priceKr())))
                .toList();

        int    myBfb  = (int) Math.round(car.rangeKm() * 100_000.0 / car.priceKr());
        int    myRank = ranked.stream().map(CarSpec::name).toList().indexOf(car.name()) + 1;
        int    total  = ranked.size();
        CarSpec best  = ranked.get(0);
        int    bestBfb = (int) Math.round(best.rangeKm() * 100_000.0 / best.priceKr());

        return String.format(
            "%s ger %d km per 100 000 kr – placering %d av %d bilar. Bäst värde: %s (%d km/100 tkr).",
            car.name(), myBfb, myRank, total, best.name(), bestBfb);
    }

    private double extractPrice(String cost) {
        if (cost == null || cost.isBlank()) return Double.MAX_VALUE;
        String l = cost.toLowerCase();
        if (l.contains("free") || l.contains("fri") || l.contains("gratis")) return 0;
        var m = java.util.regex.Pattern.compile("(\\d+[.,]?\\d*)").matcher(cost);
        return m.find() ? Double.parseDouble(m.group(1).replace(",", ".")) : Double.MAX_VALUE;
    }
}
