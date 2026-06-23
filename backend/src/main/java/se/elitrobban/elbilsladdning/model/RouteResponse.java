package se.elitrobban.elbilsladdning.model;

import java.util.List;

public record RouteResponse(double totalDistanceKm, int stopsNeeded, String carName, List<RouteStop> stops) {}
