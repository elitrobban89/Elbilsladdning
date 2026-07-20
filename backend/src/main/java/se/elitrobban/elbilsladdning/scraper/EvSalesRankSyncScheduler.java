package se.elitrobban.elbilsladdning.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
}
