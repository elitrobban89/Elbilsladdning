package se.elitrobban.elbilsladdning.model;

import java.util.List;

public record CarSpec(
        String name,
        double maxAcKw,
        double maxDcKw,
        List<String> connectors,
        double batteryKwh
) {
    public double maxKwForType(String type) {
        return "type2".equals(type) ? maxAcKw : maxDcKw;
    }
}
