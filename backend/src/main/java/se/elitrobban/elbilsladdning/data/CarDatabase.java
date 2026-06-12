package se.elitrobban.elbilsladdning.data;

import se.elitrobban.elbilsladdning.model.CarSpec;

import java.util.List;

public class CarDatabase {

    public static final List<CarSpec> CARS = List.of(
        //                                              AC kW   DC kW  connectors                      batteri (netto kWh)  räckvidd (WLTP km)

        // Škoda
        new CarSpec("Škoda Elroq 85",                  11.0,  175.0, List.of("type2","ccs"),           82.0,  560),
        new CarSpec("Škoda Enyaq iV 85",               11.0,  175.0, List.of("type2","ccs"),           82.0,  550),

        // Volvo
        new CarSpec("Volvo EX30",                      11.0,  153.0, List.of("type2","ccs"),           62.0,  480),
        new CarSpec("Volvo EX40",                      11.0,  150.0, List.of("type2","ccs"),           75.0,  530),
        new CarSpec("Volvo EX90",                      11.0,  250.0, List.of("type2","ccs"),          111.0,  590),

        // Tesla
        new CarSpec("Tesla Model Y",                   11.0,  250.0, List.of("type2","ccs"),           75.0,  533),
        new CarSpec("Tesla Model 3",                   11.0,  250.0, List.of("type2","ccs"),           75.0,  566),
        new CarSpec("Tesla Model S",                   11.0,  250.0, List.of("type2","ccs"),          100.0,  634),

        // Volkswagen
        new CarSpec("Volkswagen ID.3",                 11.0,  130.0, List.of("type2","ccs"),           77.0,  550),
        new CarSpec("Volkswagen ID.4",                 11.0,  135.0, List.of("type2","ccs"),           77.0,  527),
        new CarSpec("Volkswagen ID.5",                 11.0,  135.0, List.of("type2","ccs"),           77.0,  490),
        new CarSpec("Volkswagen ID.7",                 11.0,  200.0, List.of("type2","ccs"),           82.0,  640),

        // BMW
        new CarSpec("BMW i4 eDrive40",                 11.0,  210.0, List.of("type2","ccs"),           84.0,  590),
        new CarSpec("BMW i5 eDrive40",                 11.0,  205.0, List.of("type2","ccs"),           81.0,  582),
        new CarSpec("BMW iX xDrive50",                 11.0,  200.0, List.of("type2","ccs"),          105.0,  630),
        new CarSpec("BMW iX3",                         11.0,  150.0, List.of("type2","ccs"),           74.0,  460),

        // Audi
        new CarSpec("Audi Q4 e-tron",                  11.0,  135.0, List.of("type2","ccs"),           77.0,  527),
        new CarSpec("Audi Q8 e-tron",                  22.0,  170.0, List.of("type2","ccs"),           95.0,  582),

        // Hyundai / Kia
        new CarSpec("Hyundai IONIQ 5",                 11.0,  220.0, List.of("type2","ccs"),           77.4,  507),
        new CarSpec("Hyundai IONIQ 6",                 11.0,  233.0, List.of("type2","ccs"),           77.4,  614),
        new CarSpec("Kia EV6",                         11.0,  233.0, List.of("type2","ccs"),           77.4,  528),
        new CarSpec("Kia EV9",                         11.0,  240.0, List.of("type2","ccs"),           99.8,  563),

        // Polestar
        new CarSpec("Polestar 2",                      11.0,  205.0, List.of("type2","ccs"),           82.0,  560),
        new CarSpec("Polestar 3",                      11.0,  250.0, List.of("type2","ccs"),          111.0,  560),

        // Mercedes
        new CarSpec("Mercedes EQA 250",                11.0,  100.0, List.of("type2","ccs"),           67.0,  428),
        new CarSpec("Mercedes EQC",                    11.0,  110.0, List.of("type2","ccs"),           80.0,  415),
        new CarSpec("Mercedes EQE 350",                11.0,  170.0, List.of("type2","ccs"),           91.0,  660),

        // Ford
        new CarSpec("Ford Mustang Mach-E",             11.0,  150.0, List.of("type2","ccs"),           91.0,  540),

        // BYD
        new CarSpec("BYD Atto 3",                      11.0,  100.0, List.of("type2","ccs"),           60.5,  420),
        new CarSpec("BYD Seal",                        11.0,  150.0, List.of("type2","ccs"),           82.5,  570),

        // MG
        new CarSpec("MG4 Long Range",                  11.0,  150.0, List.of("type2","ccs"),           77.0,  450),

        // Porsche
        new CarSpec("Porsche Taycan",                  11.0,  270.0, List.of("type2","ccs"),           93.0,  590),

        // Toyota / Subaru
        new CarSpec("Toyota bZ4X",                     11.0,  150.0, List.of("type2","ccs"),           71.4,  452),

        // Cupra / Renault
        new CarSpec("Cupra Born",                      11.0,  170.0, List.of("type2","ccs"),           77.0,  550),
        new CarSpec("Renault Megane E-Tech",            22.0,  130.0, List.of("type2","ccs"),           60.0,  450),
        new CarSpec("Renault Zoe",                     22.0,   50.0, List.of("type2","ccs"),           50.0,  395),

        // Nissan
        new CarSpec("Nissan Ariya",                    22.0,  130.0, List.of("type2","ccs"),           87.0,  533),
        new CarSpec("Nissan Leaf (50 kWh)",             6.6,   50.0, List.of("type2","chademo"),        40.0,  270),

        // Övrigt
        new CarSpec("Fiat 500e",                       11.0,   85.0, List.of("type2","ccs"),           42.0,  320),
        new CarSpec("MINI Electric",                   11.0,   50.0, List.of("type2","ccs"),           28.9,  234)
    );
}
