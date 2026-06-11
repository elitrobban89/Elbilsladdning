package se.elitrobban.elbilsladdning.model;

public record StationDto(
        String name,
        String address,
        double distanceKm,
        double maxEffKw,
        double stationKw,
        String connectorType,
        String operator,
        String usageCost,
        String chargepricePerKwh
) {
    public String bestPrice() {
        if (chargepricePerKwh != null && !chargepricePerKwh.isBlank()) return chargepricePerKwh;
        if (usageCost         != null && !usageCost.isBlank())         return usageCost;
        return null;
    }
}
