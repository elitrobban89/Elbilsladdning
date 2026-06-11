package se.elitrobban.elbilsladdning.model;

import java.util.List;

public record StationResponse(
        String carName,
        List<StationDto> stations,
        String recommendation
) {}
