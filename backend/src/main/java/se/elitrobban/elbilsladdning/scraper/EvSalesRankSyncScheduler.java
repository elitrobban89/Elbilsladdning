package se.elitrobban.elbilsladdning.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** @author Robert Andersson Kopler */
@Component
public class EvSalesRankSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvSalesRankSyncScheduler.class);
    private final EvSalesRankSyncService service;

    public EvSalesRankSyncScheduler(EvSalesRankSyncService service) {
        this.service = service;
    }

    // Den 6:e varje månad 05:30 Stockholm — efter CarAdvice Mobility Sweden-synken (4:e 05:00),
    // ger elbilsvaruhuset.se tid att ha uppdaterat sitt inlägg med senaste periodens siffror
    @Scheduled(cron = "0 30 5 6 * *", zone = "Europe/Stockholm")
    public void monthlySync() {
        log.info("Monthly elbilsvaruhuset EV sales rank sync triggered");
        Object status = service.syncNow().get("status");
        log.info("Monthly elbilsvaruhuset EV sales rank sync finished — status {}", status);
    }

    /**
     * Synka också vid varje uppvakning. Tjänsten sover på gratisplanen, och en cron den 6:e
     * kl. 05:30 slår bara till om någon råkar ha väckt den just då — annars väntar listan en
     * månad till. Anropet går till CarAdvice, som cachar rapporten i tolv timmar, så det
     * kostar en lätt GET per uppvakning.
     *
     * <p>Bara på Render (miljövariabeln {@code RENDER}): tester och lokala körningar ska inte
     * gå ut mot nätet, och de ska inte skriva över en lokal databas med driftdata.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void synkaVidStart() {
        if (System.getenv("RENDER") == null) return;
        Thread.ofVirtual().name("elbilsranking-start").start(() -> {
            Object status = service.syncNow().get("status");
            log.info("Elbilsrankingen synkad vid uppvakning — status {}", status);
        });
    }
}
