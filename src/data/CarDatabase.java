package data;

import model.CarModel;
import model.ConnectorType;

import java.util.List;
import java.util.Set;

public class CarDatabase {

    public static final List<CarModel> CARS = List.of(
        new CarModel("Škoda Elroq 85",        11.0, 175.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Volvo EX30",             11.0, 153.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Volvo EX40",             11.0, 150.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Tesla Model Y",          11.0, 250.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Tesla Model 3",          11.0, 250.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Volkswagen ID.4",        11.0, 135.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Volkswagen ID.3",        11.0, 130.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("BMW iX3",                11.0, 150.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Audi Q8 e-tron",         22.0, 170.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Hyundai IONIQ 5",        11.0, 220.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Hyundai IONIQ 6",        11.0, 233.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Kia EV6",                11.0, 233.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Kia EV9",                11.0, 240.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Polestar 2",             11.0, 205.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("BYD Atto 3",             11.0, 100.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("BYD Seal",               11.0, 150.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Nissan Leaf (50 kWh)",    6.6,  50.0, Set.of(ConnectorType.TYPE2, ConnectorType.CHADEMO)),
        new CarModel("Mercedes EQC",           11.0, 110.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("Renault Zoe",            22.0,  50.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS)),
        new CarModel("MINI Electric",          11.0,  50.0, Set.of(ConnectorType.TYPE2, ConnectorType.CCS))
    );
}
