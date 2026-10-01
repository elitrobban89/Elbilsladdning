package se.elitrobban.elbilsladdning.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.elitrobban.elbilsladdning.service.CarSpecService;
import se.elitrobban.elbilsladdning.service.ElbilsmarknadService;

import java.util.LinkedHashMap;
import java.util.Map;

/** @author Robert Andersson Kopler */
@RestController
@RequestMapping("/api")
public class ElbilsmarknadController {

    private final ElbilsmarknadService marknad;
    private final CarSpecService carSpecService;

    public ElbilsmarknadController(ElbilsmarknadService marknad, CarSpecService carSpecService) {
        this.marknad = marknad;
        this.carSpecService = carSpecService;
    }

    /**
     * Ny- och begagnatpris från Blocket för en bil ur billistan.
     *
     * <p><b>Bara namn som finns i billistan godtas.</b> Varje okänt namn hade varit två nya
     * Blocket-anrop förbi cachen, och listan sätter då ett tak på hur många det kan bli per dygn.
     */
    @GetMapping("/car-market")
    public ResponseEntity<?> carMarket(@RequestParam String car) {
        boolean kand = carSpecService.getCars().stream().anyMatch(c -> c.name().equals(car));
        if (!kand) return ResponseEntity.badRequest().body(Map.of("error", "okänd bil"));

        return marknad.forBil(car)
                .<ResponseEntity<?>>map(b -> {
                    Map<String, Object> svar = new LinkedHashMap<>();
                    svar.put("bil", b.bil());
                    svar.put("modell", b.modell());
                    svar.put("ny", b.ny());
                    svar.put("begagnad", b.begagnad());
                    svar.put("kalla", ElbilsmarknadService.KALLA);
                    svar.put("hamtad", b.hamtadMs());
                    return ResponseEntity.ok(svar);
                })
                .orElseGet(() -> ResponseEntity.status(502).body(Map.of("error", "Blocket svarade inte")));
    }
}
