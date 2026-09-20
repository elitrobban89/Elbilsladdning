package se.elitrobban.elbilsladdning.model;

import java.util.List;

/** @author Robert Andersson Kopler */
public record RouteResponse(double totalDistanceKm, int stopsNeeded, String carName, List<RouteStop> stops) {}
