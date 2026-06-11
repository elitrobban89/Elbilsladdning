package se.elitrobban.elbilsladdning.data;

import se.elitrobban.elbilsladdning.model.CarSpec;

import java.util.List;

public class CarDatabase {

    public static final List<CarSpec> CARS = List.of(
        //                                            AC kW   DC kW  connectors                     batteri (netto kWh)
        new CarSpec("Škoda Elroq 85",                 11.0,  175.0, List.of("type2","ccs"),          82.0),
        new CarSpec("Volvo EX30",                     11.0,  153.0, List.of("type2","ccs"),          62.0),
        new CarSpec("Volvo EX40",                     11.0,  150.0, List.of("type2","ccs"),          75.0),
        new CarSpec("Tesla Model Y",                  11.0,  250.0, List.of("type2","ccs"),          75.0),
        new CarSpec("Tesla Model 3",                  11.0,  250.0, List.of("type2","ccs"),          75.0),
        new CarSpec("Volkswagen ID.4",                11.0,  135.0, List.of("type2","ccs"),          77.0),
        new CarSpec("Volkswagen ID.3",                11.0,  130.0, List.of("type2","ccs"),          77.0),
        new CarSpec("BMW iX3",                        11.0,  150.0, List.of("type2","ccs"),          74.0),
        new CarSpec("Audi Q8 e-tron",                 22.0,  170.0, List.of("type2","ccs"),          95.0),
        new CarSpec("Hyundai IONIQ 5",                11.0,  220.0, List.of("type2","ccs"),          77.4),
        new CarSpec("Hyundai IONIQ 6",                11.0,  233.0, List.of("type2","ccs"),          77.4),
        new CarSpec("Kia EV6",                        11.0,  233.0, List.of("type2","ccs"),          77.4),
        new CarSpec("Kia EV9",                        11.0,  240.0, List.of("type2","ccs"),          99.8),
        new CarSpec("Polestar 2",                     11.0,  205.0, List.of("type2","ccs"),          82.0),
        new CarSpec("BYD Atto 3",                     11.0,  100.0, List.of("type2","ccs"),          60.5),
        new CarSpec("BYD Seal",                       11.0,  150.0, List.of("type2","ccs"),          82.5),
        new CarSpec("Nissan Leaf (50 kWh)",            6.6,   50.0, List.of("type2","chademo"),      40.0),
        new CarSpec("Mercedes EQC",                   11.0,  110.0, List.of("type2","ccs"),          80.0),
        new CarSpec("Renault Zoe",                    22.0,   50.0, List.of("type2","ccs"),          50.0),
        new CarSpec("MINI Electric",                  11.0,   50.0, List.of("type2","ccs"),          28.9)
    );
}
