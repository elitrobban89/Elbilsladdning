package se.elitrobban.elbilsladdning.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.service.CarSpecService;
import se.elitrobban.elbilsladdning.service.RouteService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RouteController {

    private final RouteService   routeService;
    private final CarSpecService carSpecService;

    public RouteController(RouteService routeService, CarSpecService carSpecService) {
        this.routeService   = routeService;
        this.carSpecService = carSpecService;
    }

    @GetMapping("/route-stations")
    public ResponseEntity<?> routeStations(
            @RequestParam double startLat,
            @RequestParam double startLon,
            @RequestParam double endLat,
            @RequestParam double endLon,
            @RequestParam(required = false) Integer carIndex,
            @RequestParam(required = false) Integer rangeKm) {

        CarSpec car;
        if (carIndex != null) {
            List<CarSpec> cars = carSpecService.getCars();
            if (carIndex < 0 || carIndex >= cars.size())
                return ResponseEntity.badRequest().body(Map.of("error", "Ogiltig bilindex"));
            car = cars.get(carIndex);
        } else {
            // External consumers (Bilresa's calculator) don't know our car list —
            // a generic all-connector EV with clamped range works for stop planning
            int range = rangeKm != null ? Math.max(100, Math.min(800, rangeKm)) : 400;
            car = new CarSpec("Generisk elbil", 22, 400, List.of("ccs", "chademo", "type2"), 0, range, 0);
        }

        return ResponseEntity.ok(routeService.plan(startLat, startLon, endLat, endLon, car));
    }
}
