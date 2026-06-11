package model;

import java.util.List;

public record ChargingStation(
        String name,
        String address,
        double distanceKm,
        List<ConnectionPoint> connections,
        String usageCost,
        String operator
) {}
