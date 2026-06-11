package se.elitrobban.elbilsladdning.controller;

import org.springframework.web.bind.annotation.*;
import se.elitrobban.elbilsladdning.data.CarDatabase;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.StationDto;
import se.elitrobban.elbilsladdning.model.StationResponse;
import se.elitrobban.elbilsladdning.service.ApiNinjasService;
import se.elitrobban.elbilsladdning.service.ChargepriceService;
import se.elitrobban.elbilsladdning.service.GroqService;
import se.elitrobban.elbilsladdning.service.OcmService;
import se.elitrobban.elbilsladdning.service.OperatorPriceService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChargingController {

    private final OcmService           ocm;
    private final GroqService          groq;
    private final ChargepriceService   chargeprice;
    private final ApiNinjasService     apiNinjas;
    private final OperatorPriceService operatorPrices;

    public ChargingController(OcmService ocm, GroqService groq, ChargepriceService chargeprice,
                              ApiNinjasService apiNinjas, OperatorPriceService operatorPrices) {
        this.ocm            = ocm;
        this.groq           = groq;
        this.chargeprice    = chargeprice;
        this.apiNinjas      = apiNinjas;
        this.operatorPrices = operatorPrices;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/cars")
    public List<Map<String, Object>> cars() {
        return CarDatabase.CARS.stream().map(c -> Map.<String, Object>of(
                "name",       c.name(),
                "maxAcKw",    c.maxAcKw(),
                "maxDcKw",    c.maxDcKw(),
                "connectors", c.connectors()
        )).toList();
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

        stations = stations.stream().limit(10).map(s -> {
            // 1. Chargeprice (live, structured)
            String price = chargeprice.getPricePerKwh(s, car);

            // 2. API Ninjas (live, free-text) — only if Chargeprice has nothing
            if (price == null && apiNinjas.isEnabled() && !city.isBlank())
                price = apiNinjas.getPricing(city, s.lat(), s.lon());

            // 3. Static operator table — match on operator name, then station name
            if (price == null)
                price = operatorPrices.getApproxPrice(s.operator(), s.name());

            return price != null
                    ? new StationDto(s.name(), s.address(), s.distanceKm(),
                                     s.lat(), s.lon(), s.maxEffKw(), s.stationKw(),
                                     s.connectorType(), s.operator(), s.usageCost(), price)
                    : s;
        }).toList();

        String recommendation = groq.recommend(car, stations);

        return new StationResponse(car.name(), stations, recommendation);
    }

    private List<StationDto> sorted(List<StationDto> list, String sort) {
        Comparator<StationDto> cmp = switch (sort) {
            case "distance" -> Comparator.comparingDouble(StationDto::distanceKm);
            case "price"    -> Comparator.comparingDouble(s -> extractPrice(s.bestPrice()));
            default         -> Comparator.comparingDouble((StationDto s) -> -s.maxEffKw());
        };
        return list.stream().sorted(cmp).toList();
    }

    private double extractPrice(String cost) {
        if (cost == null || cost.isBlank()) return Double.MAX_VALUE;
        String l = cost.toLowerCase();
        if (l.contains("free") || l.contains("fri") || l.contains("gratis")) return 0;
        var m = java.util.regex.Pattern.compile("(\\d+[.,]?\\d*)").matcher(cost);
        return m.find() ? Double.parseDouble(m.group(1).replace(",", ".")) : Double.MAX_VALUE;
    }
}
