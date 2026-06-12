package se.elitrobban.elbilsladdning.data;

import se.elitrobban.elbilsladdning.model.CarSpec;

import java.util.List;

public class CarDatabase {

    public static final List<CarSpec> CARS = List.of(
        //                                              AC kW   DC kW  connectors                      batteri (netto kWh)  räckvidd (WLTP km)

        // Škoda
        new CarSpec("Škoda Elroq 85",                  11.0,  175.0, List.of("type2","ccs"),           82.0,  560),
        new CarSpec("Škoda Enyaq iV 85",               11.0,  175.0, List.of("type2","ccs"),           82.0,  550),
        new CarSpec("Škoda Enyaq iV 60",               11.0,  135.0, List.of("type2","ccs"),           58.0,  390),

        // Volvo
        new CarSpec("Volvo EX30 Single Motor",         11.0,  153.0, List.of("type2","ccs"),           49.0,  344),
        new CarSpec("Volvo EX30 Extended Range",       11.0,  153.0, List.of("type2","ccs"),           62.0,  480),
        new CarSpec("Volvo EX30 Twin Motor",           11.0,  200.0, List.of("type2","ccs"),           62.0,  460),
        new CarSpec("Volvo EX40 Single Motor",         11.0,  150.0, List.of("type2","ccs"),           75.0,  530),
        new CarSpec("Volvo EX40 Twin Motor",           11.0,  150.0, List.of("type2","ccs"),           75.0,  508),
        new CarSpec("Volvo C40 Single Motor",          11.0,  150.0, List.of("type2","ccs"),           75.0,  530),
        new CarSpec("Volvo C40 Twin Motor",            11.0,  150.0, List.of("type2","ccs"),           75.0,  502),
        new CarSpec("Volvo EX60",                      22.0,  250.0, List.of("type2","ccs"),          100.0,  600),
        new CarSpec("Volvo EX90 Twin Motor",           11.0,  250.0, List.of("type2","ccs"),          111.0,  580),
        new CarSpec("Volvo EX90 Twin Motor Performance", 11.0, 250.0, List.of("type2","ccs"),         111.0,  560),

        // Tesla
        new CarSpec("Tesla Model Y",                   11.0,  250.0, List.of("type2","ccs"),           75.0,  533),
        new CarSpec("Tesla Model 3",                   11.0,  250.0, List.of("type2","ccs"),           75.0,  566),
        new CarSpec("Tesla Model S",                   11.0,  250.0, List.of("type2","ccs"),          100.0,  634),

        // Volkswagen
        new CarSpec("Volkswagen ID.3",                 11.0,  130.0, List.of("type2","ccs"),           77.0,  550),
        new CarSpec("Volkswagen ID.4",                 11.0,  135.0, List.of("type2","ccs"),           77.0,  527),
        new CarSpec("Volkswagen ID.5",                 11.0,  135.0, List.of("type2","ccs"),           77.0,  490),
        new CarSpec("Volkswagen ID.7",                 11.0,  200.0, List.of("type2","ccs"),           82.0,  640),
        new CarSpec("Volkswagen ID.Buzz",              11.0,  200.0, List.of("type2","ccs"),           82.0,  459),

        // BMW
        new CarSpec("BMW i4 eDrive40",                 11.0,  210.0, List.of("type2","ccs"),           84.0,  590),
        new CarSpec("BMW i5 eDrive40",                 11.0,  205.0, List.of("type2","ccs"),           81.0,  582),
        new CarSpec("BMW iX xDrive50",                 11.0,  200.0, List.of("type2","ccs"),          105.0,  630),
        new CarSpec("BMW iX3",                         11.0,  150.0, List.of("type2","ccs"),           74.0,  460),
        new CarSpec("BMW iX1",                         11.0,  130.0, List.of("type2","ccs"),           64.7,  440),
        new CarSpec("BMW i7 xDrive60",                 11.0,  195.0, List.of("type2","ccs"),          101.7,  625),

        // Audi
        new CarSpec("Audi Q4 e-tron",                  11.0,  135.0, List.of("type2","ccs"),           77.0,  527),
        new CarSpec("Audi Q6 e-tron",                  22.0,  270.0, List.of("type2","ccs"),          100.0,  636),
        new CarSpec("Audi Q8 e-tron",                  22.0,  170.0, List.of("type2","ccs"),           95.0,  582),

        // Hyundai / Kia
        new CarSpec("Hyundai IONIQ 5",                 11.0,  220.0, List.of("type2","ccs"),           77.4,  507),
        new CarSpec("Hyundai IONIQ 6",                 11.0,  233.0, List.of("type2","ccs"),           77.4,  614),
        new CarSpec("Kia EV6",                         11.0,  233.0, List.of("type2","ccs"),           77.4,  528),
        new CarSpec("Kia EV9",                         11.0,  240.0, List.of("type2","ccs"),           99.8,  563),
        new CarSpec("Kia EV3 Long Range",              11.0,  101.0, List.of("type2","ccs"),           81.4,  605),
        new CarSpec("Kia Niro EV",                     11.0,   80.0, List.of("type2","ccs"),           64.8,  463),

        // Polestar
        new CarSpec("Polestar 2",                      11.0,  205.0, List.of("type2","ccs"),           82.0,  560),
        new CarSpec("Polestar 3",                      11.0,  250.0, List.of("type2","ccs"),          111.0,  560),
        new CarSpec("Polestar 4",                      11.0,  200.0, List.of("type2","ccs"),          100.0,  620),

        // Mercedes
        new CarSpec("Mercedes EQA 250",                11.0,  100.0, List.of("type2","ccs"),           67.0,  428),
        new CarSpec("Mercedes EQB",                    11.0,  100.0, List.of("type2","ccs"),           66.5,  419),
        new CarSpec("Mercedes EQC",                    11.0,  110.0, List.of("type2","ccs"),           80.0,  415),
        new CarSpec("Mercedes EQE 350",                11.0,  170.0, List.of("type2","ccs"),           91.0,  660),
        new CarSpec("Mercedes EQS 450+",               22.0,  200.0, List.of("type2","ccs"),          107.8,  784),

        // Genesis
        new CarSpec("Genesis GV60",                    11.0,  233.0, List.of("type2","ccs"),           77.4,  517),

        // Ford
        new CarSpec("Ford Mustang Mach-E",             11.0,  150.0, List.of("type2","ccs"),           91.0,  540),

        // BYD
        new CarSpec("BYD Atto 3",                      11.0,  100.0, List.of("type2","ccs"),           60.5,  420),
        new CarSpec("BYD Seal",                        11.0,  150.0, List.of("type2","ccs"),           82.5,  570),
        new CarSpec("BYD Dolphin",                      7.0,   88.0, List.of("type2","ccs"),           44.9,  427),

        // MG
        new CarSpec("MG ZS EV",                        11.0,   92.0, List.of("type2","ccs"),           72.6,  440),
        new CarSpec("MG Marvel R",                     11.0,   92.0, List.of("type2","ccs"),           75.0,  402),
        new CarSpec("MG4 Standard Range",              11.0,  117.0, List.of("type2","ccs"),           51.0,  350),
        new CarSpec("MG4 Long Range",                  11.0,  150.0, List.of("type2","ccs"),           64.0,  450),
        new CarSpec("MG4 Extended Range",              11.0,  150.0, List.of("type2","ccs"),           77.0,  520),
        new CarSpec("MG4 XPower",                      11.0,  140.0, List.of("type2","ccs"),           64.0,  385),
        new CarSpec("MG5 Standard Range",              11.0,   87.0, List.of("type2","ccs"),           50.3,  320),
        new CarSpec("MG5 Long Range",                  11.0,   87.0, List.of("type2","ccs"),           61.1,  400),
        new CarSpec("MG Cyberster",                    11.0,  144.0, List.of("type2","ccs"),           77.0,  520),

        // Porsche
        new CarSpec("Porsche Taycan",                  11.0,  270.0, List.of("type2","ccs"),           93.0,  590),
        new CarSpec("Porsche Macan Electric",          22.0,  270.0, List.of("type2","ccs"),          100.0,  613),

        // Toyota / Subaru
        new CarSpec("Toyota bZ4X",                     11.0,  150.0, List.of("type2","ccs"),           71.4,  452),

        // Cupra / Renault
        new CarSpec("Cupra Born",                      11.0,  170.0, List.of("type2","ccs"),           77.0,  550),
        new CarSpec("Renault Megane E-Tech",            22.0,  130.0, List.of("type2","ccs"),           60.0,  450),
        new CarSpec("Renault Zoe",                     22.0,   50.0, List.of("type2","ccs"),           50.0,  395),

        // Nissan
        new CarSpec("Nissan Ariya",                    22.0,  130.0, List.of("type2","ccs"),           87.0,  533),
        new CarSpec("Nissan Leaf (50 kWh)",             6.6,   50.0, List.of("type2","chademo"),        40.0,  270),

        // Smart
        new CarSpec("Smart #1",                        22.0,  150.0, List.of("type2","ccs"),           66.0,  440),
        new CarSpec("Smart #3",                        22.0,  150.0, List.of("type2","ccs"),           66.0,  455),

        // Honda
        new CarSpec("Honda e:Ny1",                     11.0,   78.0, List.of("type2","ccs"),           68.8,  412),

        // Xpeng
        new CarSpec("Xpeng G6",                        11.0,  250.0, List.of("type2","ccs"),           87.5,  570),

        // Övrigt
        new CarSpec("Fiat 500e",                       11.0,   85.0, List.of("type2","ccs"),           42.0,  320),
        new CarSpec("MINI Electric",                   11.0,   50.0, List.of("type2","ccs"),           28.9,  234)
    );
}
