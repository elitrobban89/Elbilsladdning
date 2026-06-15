package se.elitrobban.elbilsladdning.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import se.elitrobban.elbilsladdning.data.CarDatabase;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;
import se.elitrobban.elbilsladdning.model.StationResponse;
import se.elitrobban.elbilsladdning.service.ApiNinjasService;
import se.elitrobban.elbilsladdning.service.ChargepriceService;
import se.elitrobban.elbilsladdning.service.GroqService;
import se.elitrobban.elbilsladdning.service.NobilService;
import se.elitrobban.elbilsladdning.service.OcmService;
import se.elitrobban.elbilsladdning.service.OperatorPriceService;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ChargingController {

    private static final int CHAT_RATE_LIMIT = 10;
    private static final long WINDOW_MS = 60_000L;
    private final ConcurrentHashMap<String, Deque<Long>> chatTimestamps = new ConcurrentHashMap<>();

    private final OcmService           ocm;
    private final GroqService          groq;
    private final ChargepriceService   chargeprice;
    private final ApiNinjasService     apiNinjas;
    private final OperatorPriceService operatorPrices;
    private final NobilService         nobil;

    public ChargingController(OcmService ocm, GroqService groq, ChargepriceService chargeprice,
                              ApiNinjasService apiNinjas, OperatorPriceService operatorPrices,
                              NobilService nobil) {
        this.ocm            = ocm;
        this.groq           = groq;
        this.chargeprice    = chargeprice;
        this.apiNinjas      = apiNinjas;
        this.operatorPrices = operatorPrices;
        this.nobil          = nobil;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
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
        return Map.of("reply", groq.chat(messages, CarDatabase.CARS));
    }

    @GetMapping("/cars")
    public List<Map<String, Object>> cars() {
        return CarDatabase.CARS.stream().map(c -> {
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
    public StationResponse stations(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam int carIndex,
            @RequestParam(defaultValue = "speed") String sort,
            @RequestParam(defaultValue = "") String city) {

        if (carIndex < 0 || carIndex >= CarDatabase.CARS.size())
            throw new IllegalArgumentException("Ogiltigt bilindex: " + carIndex);

        CarSpec car = CarDatabase.CARS.get(carIndex);

        List<StationDto> stations = ocm.findNearby(lat, lon, car);
        stations = sorted(stations, sort);

        // Fetch NOBIL data in parallel with price enrichment (non-blocking fallback)
        List<NobilService.NobilStation> nobilStations = nobil.getStations(lat, lon);

        stations = stations.stream().limit(10).map(s -> {
            // 1. Chargeprice (live, structured)
            String price = chargeprice.getPricePerKwh(s, car);

            // 2. API Ninjas (live, free-text) — only if Chargeprice has nothing
            if (price == null && apiNinjas.isEnabled() && !city.isBlank())
                price = apiNinjas.getPricing(city, s.lat(), s.lon());

            // 3. Static operator table — match on operator name, then station name
            if (price == null)
                price = operatorPrices.getApproxPrice(s.operator(), s.name());

            // 4. Match with nearest NOBIL station within 150 m for connector count; fall back to OCM count
            int connCount = nobilStations.stream()
                    .filter(n -> NobilService.distanceKm(s.lat(), s.lon(), n.lat(), n.lon()) < 0.15)
                    .mapToInt(NobilService.NobilStation::connectorCount)
                    .max()
                    .orElse(s.connectorCount());

            String finalPrice = price != null ? price : s.chargepricePerKwh();
            return new StationDto(s.name(), s.address(), s.distanceKm(),
                                  s.lat(), s.lon(), s.maxEffKw(), s.stationKw(),
                                  s.connectorType(), s.operator(), s.usageCost(),
                                  finalPrice, connCount);
        }).toList();

        var groqResult = groq.recommend(car, stations, buildCostComparison(car));

        return new StationResponse(car.name(), stations, groqResult.recommendation(), groqResult.funFact(), buildCarFact(car));
    }

    private String buildCostComparison(CarSpec selected) {
        double selCpm = costPerMil(selected);
        if (selCpm <= 0) return null;

        var others = CarDatabase.CARS.stream()
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

        var ranked = CarDatabase.CARS.stream()
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
