package se.elitrobban.elbilsladdning.model;

/** @author Robert Andersson Kopler */
public record RouteStop(int order, double distanceFromStartKm, StationDto station) {}
