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
            @RequestParam int carIndex) {

        List<CarSpec> cars = carSpecService.getCars();
        if (carIndex < 0 || carIndex >= cars.size())
            return ResponseEntity.badRequest().body(Map.of("error", "Ogiltig bilindex"));

        return ResponseEntity.ok(routeService.plan(startLat, startLon, endLat, endLon, cars.get(carIndex)));
    }
}
