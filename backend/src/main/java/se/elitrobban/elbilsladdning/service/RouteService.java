package se.elitrobban.elbilsladdning.service;

import org.springframework.stereotype.Service;
import se.elitrobban.elbilsladdning.model.CarSpec;
import se.elitrobban.elbilsladdning.model.RouteResponse;
import se.elitrobban.elbilsladdning.model.RouteStop;
import se.elitrobban.elbilsladdning.model.StationDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RouteService {

    private final OcmService ocmService;
    private final OperatorPriceService operatorPrices;

    public RouteService(OcmService ocmService, OperatorPriceService operatorPrices) {
        this.ocmService = ocmService;
        this.operatorPrices = operatorPrices;
    }

    public RouteResponse plan(double startLat, double startLon, double endLat, double endLon, CarSpec car) {
        double totalKm = haversine(startLat, startLon, endLat, endLon);
        // Use 75% of WLTP as realistic hop distance (covers winter/highway conditions)
        double hopKm = car.rangeKm() * 0.75;
        int stopsNeeded = (int) Math.max(0, Math.ceil(totalKm / hopKm) - 1);

        List<RouteStop> stops = new ArrayList<>();
        for (int i = 1; i <= stopsNeeded; i++) {
            double frac = (double) i / (stopsNeeded + 1);
            double lat = startLat + frac * (endLat - startLat);
            double lon = startLon + frac * (endLon - startLon);
            double distFromStart = Math.round(totalKm * frac * 10.0) / 10.0;

            List<StationDto> nearby = ocmService.findNearby(lat, lon, car);
            int order = i;
            nearby.stream()
                  .max(Comparator.comparingDouble(StationDto::maxEffKw))
                  .ifPresent(best -> stops.add(new RouteStop(order, distFromStart, withPrice(best))));
        }

        return new RouteResponse(Math.round(totalKm * 10.0) / 10.0, stopsNeeded, car.name(), stops);
    }

    // Route stops skip the /stations price pipeline — the static operator table
    // is free to consult and good enough for a per-stop price estimate
    private StationDto withPrice(StationDto s) {
        if (s.chargepricePerKwh() != null && !s.chargepricePerKwh().isBlank()) return s;
        String price = operatorPrices.getApproxPrice(s.operator(), s.name());
        if (price == null) return s;
        return new StationDto(s.name(), s.address(), s.distanceKm(), s.lat(), s.lon(),
                s.maxEffKw(), s.stationKw(), s.connectorType(), s.operator(), s.usageCost(),
                price, s.connectorCount(), s.ocmId());
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                   * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
