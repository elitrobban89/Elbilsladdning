package model;

public record ConnectionPoint(
        ConnectorType type,
        double powerKw,
        int quantity
) {}
