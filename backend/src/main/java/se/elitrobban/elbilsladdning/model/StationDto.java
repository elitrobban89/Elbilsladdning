package se.elitrobban.elbilsladdning.model;

public record StationDto(
        String name,
        String address,
        double distanceKm,
        double maxEffKw,
        double stationKw,
        String connectorType,
        String operator,
        String usageCost
) {}
