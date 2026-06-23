package se.elitrobban.elbilsladdning.model;

public record RouteStop(int order, double distanceFromStartKm, StationDto station) {}
