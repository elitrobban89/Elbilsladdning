package se.elitrobban.elbilsladdning.data;

import se.elitrobban.elbilsladdning.model.CarSpec;

import java.util.List;

public class CarDatabase {

    public static final List<CarSpec> CARS = List.of(
        //                                              AC kW   DC kW  connectors                      batteri kWh  WLTP km  pris kr

        // Škoda
        new CarSpec("Škoda Elroq 85",                  11.0,  175.0, List.of("type2","ccs"),           82.0,  560,  500_000),
        new CarSpec("Škoda Enyaq iV 85",               11.0,  175.0, List.of("type2","ccs"),           82.0,  550,  490_000),
        new CarSpec("Škoda Enyaq iV 60",               11.0,  135.0, List.of("type2","ccs"),           58.0,  390,  420_000),

        // Volvo
        new CarSpec("Volvo EX30 Single Motor",         11.0,  153.0, List.of("type2","ccs"),           49.0,  344,  320_000),
        new CarSpec("Volvo EX30 Extended Range",       11.0,  153.0, List.of("type2","ccs"),           62.0,  480,  350_000),
        new CarSpec("Volvo EX30 Twin Motor",           11.0,  200.0, List.of("type2","ccs"),           62.0,  460,  385_000),
        new CarSpec("Volvo EX30 Cross Country",        11.0,  153.0, List.of("type2","ccs"),           62.0,  455,  395_000),
        new CarSpec("Volvo EX40 Single Motor",         11.0,  150.0, List.of("type2","ccs"),           75.0,  530,  465_000),
        new CarSpec("Volvo EX40 Twin Motor",           11.0,  150.0, List.of("type2","ccs"),           75.0,  508,  500_000),
        new CarSpec("Volvo C40 Single Motor",          11.0,  150.0, List.of("type2","ccs"),           75.0,  530,  475_000),
        new CarSpec("Volvo C40 Twin Motor",            11.0,  150.0, List.of("type2","ccs"),           75.0,  502,  515_000),
        new CarSpec("Volvo EX60",                      22.0,  250.0, List.of("type2","ccs"),          100.0,  600,  620_000),
        new CarSpec("Volvo EX90 Twin Motor",           11.0,  250.0, List.of("type2","ccs"),          111.0,  580,  890_000),
        new CarSpec("Volvo EX90 Twin Motor Performance", 11.0, 250.0, List.of("type2","ccs"),         111.0,  560,  990_000),

        // Tesla
        new CarSpec("Tesla Model Y",                   11.0,  250.0, List.of("type2","ccs"),           75.0,  533,  499_000),
        new CarSpec("Tesla Model 3",                   11.0,  250.0, List.of("type2","ccs"),           75.0,  566,  499_000),
        new CarSpec("Tesla Model S",                   11.0,  250.0, List.of("type2","ccs"),          100.0,  634, 1_100_000),

        // Volkswagen
        new CarSpec("Volkswagen ID.3",                 11.0,  130.0, List.of("type2","ccs"),           77.0,  550,  390_000),
        new CarSpec("Volkswagen ID.4",                 11.0,  135.0, List.of("type2","ccs"),           77.0,  527,  440_000),
        new CarSpec("Volkswagen ID.5",                 11.0,  135.0, List.of("type2","ccs"),           77.0,  490,  465_000),
        new CarSpec("Volkswagen ID.7",                 11.0,  200.0, List.of("type2","ccs"),           82.0,  640,  600_000),
        new CarSpec("Volkswagen ID.Buzz",              11.0,  200.0, List.of("type2","ccs"),           82.0,  459,  570_000),

        // BMW
        new CarSpec("BMW i4 eDrive40",                 11.0,  210.0, List.of("type2","ccs"),           84.0,  590,  660_000),
        new CarSpec("BMW i5 eDrive40",                 11.0,  205.0, List.of("type2","ccs"),           81.0,  582,  810_000),
        new CarSpec("BMW iX xDrive50",                 11.0,  200.0, List.of("type2","ccs"),          105.0,  630,  920_000),
        new CarSpec("BMW iX3",                         11.0,  150.0, List.of("type2","ccs"),           74.0,  460,  610_000),
        new CarSpec("BMW iX1",                         11.0,  130.0, List.of("type2","ccs"),           64.7,  440,  530_000),
        new CarSpec("BMW i7 xDrive60",                 11.0,  195.0, List.of("type2","ccs"),          101.7,  625, 1_350_000),

        // Audi
        new CarSpec("Audi Q4 e-tron",                  11.0,  135.0, List.of("type2","ccs"),           77.0,  527,  490_000),
        new CarSpec("Audi Q6 e-tron",                  22.0,  270.0, List.of("type2","ccs"),          100.0,  636,  620_000),
        new CarSpec("Audi Q8 e-tron",                  22.0,  170.0, List.of("type2","ccs"),           95.0,  582,  860_000),

        // Hyundai / Kia
        new CarSpec("Hyundai IONIQ 5",                 11.0,  220.0, List.of("type2","ccs"),           77.4,  507,  480_000),
        new CarSpec("Hyundai IONIQ 6",                 11.0,  233.0, List.of("type2","ccs"),           77.4,  614,  480_000),
        new CarSpec("Kia EV6",                         11.0,  233.0, List.of("type2","ccs"),           77.4,  528,  460_000),
        new CarSpec("Kia EV9",                         11.0,  240.0, List.of("type2","ccs"),           99.8,  563,  760_000),
        new CarSpec("Kia EV3 Long Range",              11.0,  101.0, List.of("type2","ccs"),           81.4,  605,  370_000),
        new CarSpec("Kia Niro EV",                     11.0,   80.0, List.of("type2","ccs"),           64.8,  463,  390_000),

        // Polestar
        new CarSpec("Polestar 2",                      11.0,  205.0, List.of("type2","ccs"),           82.0,  560,  510_000),
        new CarSpec("Polestar 3",                      11.0,  250.0, List.of("type2","ccs"),          111.0,  560,  760_000),
        new CarSpec("Polestar 4",                      11.0,  200.0, List.of("type2","ccs"),          100.0,  620,  640_000),

        // Mercedes
        new CarSpec("Mercedes EQA 250",                11.0,  100.0, List.of("type2","ccs"),           67.0,  428,  490_000),
        new CarSpec("Mercedes EQB",                    11.0,  100.0, List.of("type2","ccs"),           66.5,  419,  520_000),
        new CarSpec("Mercedes EQC",                    11.0,  110.0, List.of("type2","ccs"),           80.0,  415,  610_000),
        new CarSpec("Mercedes EQE 350",                11.0,  170.0, List.of("type2","ccs"),           91.0,  660,  760_000),
        new CarSpec("Mercedes EQS 450+",               22.0,  200.0, List.of("type2","ccs"),          107.8,  784, 1_100_000),

        // Genesis
        new CarSpec("Genesis GV60",                    11.0,  233.0, List.of("type2","ccs"),           77.4,  517,  560_000),

        // Ford
        new CarSpec("Ford Mustang Mach-E",             11.0,  150.0, List.of("type2","ccs"),           91.0,  540,  540_000),

        // BYD
        new CarSpec("BYD Atto 3",                      11.0,  100.0, List.of("type2","ccs"),           60.5,  420,  310_000),
        new CarSpec("BYD Seal",                        11.0,  150.0, List.of("type2","ccs"),           82.5,  570,  390_000),
        new CarSpec("BYD Dolphin",                      7.0,   88.0, List.of("type2","ccs"),           44.9,  427,  280_000),

        // MG
        new CarSpec("MG ZS EV",                        11.0,   92.0, List.of("type2","ccs"),           72.6,  440,  300_000),
        new CarSpec("MG Marvel R",                     11.0,   92.0, List.of("type2","ccs"),           75.0,  402,  350_000),
        new CarSpec("MG4 Standard Range",              11.0,  117.0, List.of("type2","ccs"),           51.0,  350,  295_000),
        new CarSpec("MG4 Long Range",                  11.0,  150.0, List.of("type2","ccs"),           64.0,  450,  335_000),
        new CarSpec("MG4 Extended Range",              11.0,  150.0, List.of("type2","ccs"),           77.0,  520,  375_000),
        new CarSpec("MG4 XPower",                      11.0,  140.0, List.of("type2","ccs"),           64.0,  385,  405_000),
        new CarSpec("MG5 Standard Range",              11.0,   87.0, List.of("type2","ccs"),           50.3,  320,  315_000),
        new CarSpec("MG5 Long Range",                  11.0,   87.0, List.of("type2","ccs"),           61.1,  400,  355_000),
        new CarSpec("MG Cyberster",                    11.0,  144.0, List.of("type2","ccs"),           77.0,  520,  720_000),

        // Porsche
        new CarSpec("Porsche Taycan",                  11.0,  270.0, List.of("type2","ccs"),           93.0,  590, 1_100_000),
        new CarSpec("Porsche Macan Electric",          22.0,  270.0, List.of("type2","ccs"),          100.0,  613,  870_000),

        // Toyota
        new CarSpec("Toyota bZ4X",                     11.0,  150.0, List.of("type2","ccs"),           71.4,  452,  480_000),

        // Cupra / Renault
        new CarSpec("Cupra Born",                      11.0,  170.0, List.of("type2","ccs"),           77.0,  550,  380_000),
        new CarSpec("Renault Megane E-Tech",            22.0,  130.0, List.of("type2","ccs"),           60.0,  450,  380_000),
        new CarSpec("Renault Zoe",                     22.0,   50.0, List.of("type2","ccs"),           50.0,  395,  270_000),

        // Nissan
        new CarSpec("Nissan Ariya",                    22.0,  130.0, List.of("type2","ccs"),           87.0,  533,  490_000),
        new CarSpec("Nissan Leaf (50 kWh)",             6.6,   50.0, List.of("type2","chademo"),        40.0,  270,  290_000),

        // Smart
        new CarSpec("Smart #1",                        22.0,  150.0, List.of("type2","ccs"),           66.0,  440,  380_000),
        new CarSpec("Smart #3",                        22.0,  150.0, List.of("type2","ccs"),           66.0,  455,  410_000),

        // Honda
        new CarSpec("Honda e:Ny1",                     11.0,   78.0, List.of("type2","ccs"),           68.8,  412,  390_000),

        // Xpeng
        new CarSpec("Xpeng G6",                        11.0,  250.0, List.of("type2","ccs"),           87.5,  570,  470_000),

        // Övrigt
        new CarSpec("Fiat 500e",                       11.0,   85.0, List.of("type2","ccs"),           42.0,  320,  290_000),
        new CarSpec("MINI Electric",                   11.0,   50.0, List.of("type2","ccs"),           28.9,  234,  340_000)
    );
}
