package se.elitrobban.elbilsladdning.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.elitrobban.elbilsladdning.data.CarDatabase;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.EvSpecEntity;
import se.elitrobban.elbilsladdning.repository.EvSpecRepository;

import java.util.List;

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
            List<EvSpecEntity> rows = evSpecRepo.findByCarTypeOrderByCarNameAsc("EV");
            if (!rows.isEmpty()) {
                cache = rows.stream().map(this::toCarSpec).toList();
                cacheTime = now;
                log.debug("CarSpecService: loaded {} EVs from database", cache.size());
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
