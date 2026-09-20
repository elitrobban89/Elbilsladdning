package se.elitrobban.elbilsladdning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.elitrobban.elbilsladdning.service.VroomTopCarsService;

/**
 * Månadens tio mest registrerade elbilar i Sverige, med Vroom som källa.
 *
 * <p>Publik och utan nyckel av samma skäl som {@link VroomNewsController}: innehållet är
 * offentligt och karusellen läses av besökare som aldrig loggar in. Tjänsten hämtar om sig
 * själv när cachen åldrats — det är så listan blir en ny månad utan att någon rör koden.
 *
 * <p>Tom {@code bilar} betyder att ingen läsbar lista hittats ännu; frontenden ritar då ingen
 * Vroom-flik alls i stället för ett tomt kort.
 *
 * @author Robert Andersson Kopler
 */
@RestController
@RequestMapping("/api")
public class VroomTopCarsController {

    private final VroomTopCarsService service;

    public VroomTopCarsController(VroomTopCarsService service) {
        this.service = service;
    }

    @GetMapping("/vroom-top-cars")
    public VroomTopCarsService.Topplista vroomTopCars() {
        return service.senaste();
    }
}
