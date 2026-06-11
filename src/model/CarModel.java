package model;

import java.util.Set;

public record CarModel(
        String name,
        double maxAcKw,
        double maxDcKw,
        Set<ConnectorType> connectors
) {
    public double maxKwForConnector(ConnectorType type) {
        return switch (type) {
            case TYPE2          -> maxAcKw;
            case CCS, CHADEMO   -> maxDcKw;
        };
    }
}
