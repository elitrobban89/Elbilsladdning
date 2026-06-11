package se.elitrobban.elbilsladdning.data;

import se.elitrobban.elbilsladdning.model.CarSpec;

import java.util.List;

public class CarDatabase {

    public static final List<CarSpec> CARS = List.of(
        new CarSpec("Škoda Elroq 85",        11.0, 175.0, List.of("type2","ccs")),
        new CarSpec("Volvo EX30",             11.0, 153.0, List.of("type2","ccs")),
        new CarSpec("Volvo EX40",             11.0, 150.0, List.of("type2","ccs")),
        new CarSpec("Tesla Model Y",          11.0, 250.0, List.of("type2","ccs")),
        new CarSpec("Tesla Model 3",          11.0, 250.0, List.of("type2","ccs")),
        new CarSpec("Volkswagen ID.4",        11.0, 135.0, List.of("type2","ccs")),
        new CarSpec("Volkswagen ID.3",        11.0, 130.0, List.of("type2","ccs")),
        new CarSpec("BMW iX3",                11.0, 150.0, List.of("type2","ccs")),
        new CarSpec("Audi Q8 e-tron",         22.0, 170.0, List.of("type2","ccs")),
        new CarSpec("Hyundai IONIQ 5",        11.0, 220.0, List.of("type2","ccs")),
        new CarSpec("Hyundai IONIQ 6",        11.0, 233.0, List.of("type2","ccs")),
        new CarSpec("Kia EV6",                11.0, 233.0, List.of("type2","ccs")),
        new CarSpec("Kia EV9",                11.0, 240.0, List.of("type2","ccs")),
        new CarSpec("Polestar 2",             11.0, 205.0, List.of("type2","ccs")),
        new CarSpec("BYD Atto 3",             11.0, 100.0, List.of("type2","ccs")),
        new CarSpec("BYD Seal",               11.0, 150.0, List.of("type2","ccs")),
        new CarSpec("Nissan Leaf (50 kWh)",    6.6,  50.0, List.of("type2","chademo")),
        new CarSpec("Mercedes EQC",           11.0, 110.0, List.of("type2","ccs")),
        new CarSpec("Renault Zoe",            22.0,  50.0, List.of("type2","ccs")),
        new CarSpec("MINI Electric",          11.0,  50.0, List.of("type2","ccs"))
    );
}
