package se.elitrobban.elbilsladdning.model;

import java.util.List;

/**
 * @param sourceError null när allt gick bra. Sätts BARA när stationskällan fallerade, och
 *                    aldrig när den svarade med noll stationer. De två ser likadana ut i en
 *                    tom lista men betyder helt olika saker: "det finns inga laddare inom
 *                    15 km" är ett svar, "OpenChargeMap svarar inte" är ett haveri — och den
 *                    som får det första när det andra hände drar slutsatsen att appen är
 *                    trasig, eller värre, att det inte finns några laddare där.
 */
public record StationResponse(
        String carName,
        List<StationDto> stations,
        String recommendation,
        String funFact,
        String carFact,
        String sourceError
) {}
