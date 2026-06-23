package se.elitrobban.elbilsladdning.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.elitrobban.elbilsladdning.data.CarDatabase;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.service.RouteService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping("/route-stations")
    public ResponseEntity<?> routeStations(
            @RequestParam double startLat,
            @RequestParam double startLon,
            @RequestParam double endLat,
            @RequestParam double endLon,
            @RequestParam int carIndex) {

        List<CarSpec> cars = CarDatabase.CARS;
        if (carIndex < 0 || carIndex >= cars.size())
            return ResponseEntity.badRequest().body(Map.of("error", "Ogiltig bilindex"));

        return ResponseEntity.ok(routeService.plan(startLat, startLon, endLat, endLon, cars.get(carIndex)));
    }
}
