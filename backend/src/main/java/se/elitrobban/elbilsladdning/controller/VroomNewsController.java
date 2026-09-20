package se.elitrobban.elbilsladdning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.elitrobban.elbilsladdning.service.VroomNewsService;

/**
 * Senaste månadens nyheter från Vrooms pressrum.
 *
 * <p>Publik och utan nyckel: innehållet är ett publikt RSS-flöde, och karusellen läses av
 * besökare som aldrig loggar in. Ingen admin-synk behövs heller — tjänsten hämtar om sig
 * själv när cachen åldrats, vilket är vad "uppdateras varje månad" betyder i praktiken.
 * En schemalagd synk hade dessutom varit opålitlig här: tjänsten ligger på gratisnivån och
 * sover, så ett cron-uttryck garanterar ingenting.
 *
 * <p>Tomt {@code nyheter} betyder att flödet inte svarat ännu — frontenden ritar då ingen
 * flik alls i stället för en tom karusell.
 *
 * @author Robert Andersson Kopler
 */
@RestController
@RequestMapping("/api")
public class VroomNewsController {

    private final VroomNewsService service;

    public VroomNewsController(VroomNewsService service) {
        this.service = service;
    }

    @GetMapping("/vroom-news")
    public VroomNewsService.Nyheter vroomNews() {
        return service.senaste();
    }
}
