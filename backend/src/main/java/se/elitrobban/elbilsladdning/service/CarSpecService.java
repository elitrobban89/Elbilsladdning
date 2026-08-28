package se.elitrobban.elbilsladdning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.elitrobban.elbilsladdning.data.CarDatabase;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.EvSpecEntity;
import se.elitrobban.elbilsladdning.repository.EvSpecRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CarSpecService {

    private static final Logger log = LoggerFactory.getLogger(CarSpecService.class);
    private static final long CACHE_TTL_MS = 2 * 60 * 60 * 1000L;

    private final EvSpecRepository evSpecRepo;
    private volatile List<CarSpec> cache;
    private volatile long cacheTime = 0;

    public CarSpecService(EvSpecRepository evSpecRepo) {
        this.evSpecRepo = evSpecRepo;
    }

    public List<CarSpec> getCars() {
        long now = System.currentTimeMillis();
        if (cache != null && now - cacheTime < CACHE_TTL_MS) return cache;
        try {
            // NULL i car_type raknas som elbil, precis som CarAdvice gor pa samma tabell -
            // annars ar 58 bilar osynliga har men synliga dar. Se EvSpecRepository.
            List<EvSpecEntity> rows = evSpecRepo.findElbilar();
            if (!rows.isEmpty()) {
                // Deduplicate: one entry per car_name, keep the variant with highest rangeKm
                Map<String, EvSpecEntity> best = new LinkedHashMap<>();
                for (EvSpecEntity e : rows) {
                    String name = e.getCarName() != null ? e.getCarName() : "";
                    EvSpecEntity prev = best.get(name);
                    if (prev == null || (e.getRangeKm() != null &&
                            (prev.getRangeKm() == null || e.getRangeKm() > prev.getRangeKm()))) {
                        best.put(name, e);
                    }
                }
                cache = best.values().stream().map(this::toCarSpec).toList();
                cacheTime = now;
                log.debug("CarSpecService: loaded {} unique EVs from database", cache.size());
                return cache;
            }
        } catch (Exception e) {
            log.warn("CarSpecService: DB unavailable, falling back to hardcoded list — {}", e.getMessage());
        }
        return CarDatabase.CARS;
    }

    private CarSpec toCarSpec(EvSpecEntity e) {
        String name = e.getCarName() != null ? e.getCarName() : "";
        double dcKw = e.getMaxDcKw() != null ? e.getMaxDcKw() : 0.0;
        List<String> connectors;
        if (dcKw > 0) {
            boolean chademo = name.toLowerCase().contains("leaf") || name.toLowerCase().contains("chademo");
            connectors = chademo ? List.of("type2", "chademo") : List.of("type2", "ccs");
        } else {
            connectors = List.of("type2");
        }
        return new CarSpec(
            name,
            e.getMaxAcKw()   != null ? e.getMaxAcKw()   : 0.0,
            dcKw,
            connectors,
            e.getBatteryKwh() != null ? e.getBatteryKwh() : 0.0,
            e.getRangeKm()   != null ? e.getRangeKm()   : 0,
            e.getPriceKr()   != null ? e.getPriceKr()   : 0
        );
    }
}
