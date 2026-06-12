package se.elitrobban.elbilsladdning.model;

public record StationDto(
        String name,
        String address,
        double distanceKm,
        double lat,
        double lon,
        double maxEffKw,
        double stationKw,
        String connectorType,
        String operator,
        String usageCost,
        String chargepricePerKwh,
        int connectorCount          // 0 = unknown; populated from NOBIL when available
) {
    public String bestPrice() {
        if (chargepricePerKwh != null && !chargepricePerKwh.isBlank()) return chargepricePerKwh;
        if (usageCost         != null && !usageCost.isBlank())         return usageCost;
        return null;
    }
}
