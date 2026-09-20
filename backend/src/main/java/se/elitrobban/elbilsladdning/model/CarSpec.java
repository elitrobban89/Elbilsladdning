package se.elitrobban.elbilsladdning.model;

import java.util.List;

/** @author Robert Andersson Kopler */
public record CarSpec(
        String name,
        double maxAcKw,
        double maxDcKw,
        List<String> connectors,
        double batteryKwh,
        int rangeKm,
        int priceKr
) {
    public double maxKwForType(String type) {
        return "type2".equals(type) ? maxAcKw : maxDcKw;
    }
}
