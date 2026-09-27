package se.elitrobban.elbilsladdning.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.elitrobban.elbilsladdning.service.CarlaIndexService;

/**
 * Carlas Elbilsindex för faktakarusellen. Publik och utan nyckel, som Vroom-flödet: innehållet
 * är ett publikt pressmeddelande. {@code 204} när flödet inte svarat ännu — frontenden ritar då
 * ingen rad alls i stället för en rad utan siffror.
 *
 * @author Robert Andersson Kopler
 */
@RestController
@RequestMapping("/api")
public class CarlaIndexController {

    private final CarlaIndexService service;

    public CarlaIndexController(CarlaIndexService service) {
        this.service = service;
    }

    @GetMapping("/carla-index")
    public ResponseEntity<CarlaIndexService.Index> carlaIndex() {
        CarlaIndexService.Index i = service.senaste();
        return i == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(i);
    }
}
