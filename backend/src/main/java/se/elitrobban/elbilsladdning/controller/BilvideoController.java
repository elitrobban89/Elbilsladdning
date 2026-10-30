package se.elitrobban.elbilsladdning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.elitrobban.elbilsladdning.service.BilvideoService;

/**
 * Videokortet i karusellen: Alriks Bilars senaste videor plus några utvalda.
 *
 * <p>Publik och utan nyckel av samma skäl som {@link VroomTopCarsController}: innehållet är
 * offentligt och karusellen läses av besökare som aldrig loggar in.
 *
 * @author Robert Andersson Kopler
 */
@RestController
@RequestMapping("/api")
public class BilvideoController {

    private final BilvideoService service;

    public BilvideoController(BilvideoService service) {
        this.service = service;
    }

    @GetMapping("/bilvideor")
    public BilvideoService.Videolista bilvideor() {
        return service.senaste();
    }
}
