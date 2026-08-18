package se.elitrobban.elbilsladdning.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EvValueRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(EvValueRetentionScheduler.class);
    private final EvValueRetentionSyncService service;

    public EvValueRetentionScheduler(EvValueRetentionSyncService service) {
        this.service = service;
    }

    /**
     * Varje måndag 04:15 Stockholm.
     *
     * <p><b>Veckovis och inte nattligt.</b> Begagnatpriser rör sig i månader, inte timmar — en
     * daglig körning hade gett tio Blocket-anrop om dagen för siffror som ändå ser likadana ut.
     * Måndag morgon fångar helgens nya annonser.
     *
     * <p>04:15 och inte 04:00 för att inte krocka med andra jobb på samma instans.
     */
    @Scheduled(cron = "0 15 4 * * MON", zone = "Europe/Stockholm")
    public void weeklySync() {
        log.info("Veckovis värdetappssynk mot Blocket startar");
        Object status = service.syncNow().get("status");
        log.info("Veckovis värdetappssynk klar — status {}", status);
    }
}
