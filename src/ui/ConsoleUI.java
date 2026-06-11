package ui;

import config.Config;
import data.CarDatabase;
import model.*;
import service.GeocodingService;
import service.OpenChargeMapService;

import java.util.*;
import java.util.regex.*;

public class ConsoleUI {

    private final Scanner             scanner   = new Scanner(System.in);
    private final GeocodingService    geocoding = new GeocodingService();
    private final OpenChargeMapService ocm      = new OpenChargeMapService();

    public void run() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║     EV Laddningsassistent  ⚡        ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();

        CarModel car = selectCar();
        System.out.printf("%nVald: %s  (AC max: %.0f kW | DC max: %.0f kW)%n",
                car.name(), car.maxAcKw(), car.maxDcKw());

        String location = askLocation();
        String sort     = askSortOrder();

        System.out.print("\nHämtar laddstationer...");
        try {
            double[] coords;
            String displayName;
            if (location.isBlank()) {
                Object[] ip = geocoding.locateByIp();
                coords      = new double[]{(double) ip[0], (double) ip[1]};
                displayName = (String) ip[2];
            } else {
                coords      = geocoding.geocode(location);
                displayName = location;
            }
            System.out.println(" OK (" + displayName + ")");

            List<ChargingStation> stations = ocm.findNearby(coords[0], coords[1], car);


            if (stations.isEmpty()) {
                System.out.println("\nInga kompatibla laddstationer hittades inom " +
                        Config.SEARCH_RADIUS_KM + " km.");
                return;
            }

            sortStations(stations, car, sort);
            displayResults(stations, car);

        } catch (Exception e) {
            System.out.println("\nFel: " + e.getMessage());
        }
    }

    private CarModel selectCar() {
        List<CarModel> cars = CarDatabase.CARS;
        System.out.println("Välj din elbil:");
        for (int i = 0; i < cars.size(); i++) {
            System.out.printf("  %2d. %-28s  AC: %4.0f kW  DC: %4.0f kW%n",
                    i + 1,
                    cars.get(i).name(),
                    cars.get(i).maxAcKw(),
                    cars.get(i).maxDcKw());
        }
        System.out.print("\nAnge nummer (1–" + cars.size() + "): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine().strip()) - 1;
            if (choice >= 0 && choice < cars.size()) return cars.get(choice);
        } catch (NumberFormatException ignored) {}
        System.out.println("Ogiltigt val – väljer " + cars.get(0).name());
        return cars.get(0);
    }

    private String askLocation() {
        System.out.print("\nAnge plats (stad eller adress): ");
        return scanner.nextLine().strip();
    }

    private String askSortOrder() {
        System.out.println("\nSortera på:");
        System.out.println("  1. Snabbast (högst effektiv kW)");
        System.out.println("  2. Närmast  (kortast avstånd)");
        System.out.println("  3. Billigast (lägst pris/kWh när tillgängligt)");
        System.out.print("Välj (1–3): ");
        return switch (scanner.nextLine().strip()) {
            case "2" -> "distance";
            case "3" -> "price";
            default  -> "speed";
        };
    }

    private void sortStations(List<ChargingStation> stations, CarModel car, String sortBy) {
        Comparator<ChargingStation> cmp = switch (sortBy) {
            case "distance" -> Comparator.comparingDouble(ChargingStation::distanceKm);
            case "price"    -> Comparator.comparingDouble(s -> extractPrice(s.usageCost()));
            default         -> Comparator.comparingDouble(
                    (ChargingStation s) -> -maxEffectiveKw(s, car));
        };
        stations.sort(cmp);
    }

    private double maxEffectiveKw(ChargingStation s, CarModel car) {
        return s.connections().stream()
                .mapToDouble(c -> Math.min(c.powerKw(), car.maxKwForConnector(c.type())))
                .max().orElse(0);
    }

    private double extractPrice(String cost) {
        if (cost == null || cost.isBlank()) return Double.MAX_VALUE;
        String lower = cost.toLowerCase();
        if (lower.contains("free") || lower.contains("fri") || lower.contains("gratis")) return 0.0;
        Matcher m = Pattern.compile("(\\d+[.,]?\\d*)").matcher(cost);
        if (m.find()) return Double.parseDouble(m.group(1).replace(",", "."));
        return Double.MAX_VALUE;
    }

    private void displayResults(List<ChargingStation> stations, CarModel car) {
        int limit = Math.min(10, stations.size());
        System.out.println("\nTopp " + limit + " laddstationer för " + car.name() + ":");
        System.out.println("═".repeat(60));

        for (int i = 0; i < limit; i++) {
            ChargingStation s   = stations.get(i);
            double effKw        = maxEffectiveKw(s, car);
            ConnectionPoint best = s.connections().stream()
                    .max(Comparator.comparingDouble(ConnectionPoint::powerKw))
                    .orElseThrow();

            System.out.printf("%2d. %-32s  %.1f km%n",
                    i + 1, truncate(s.name(), 31), s.distanceKm());
            System.out.printf("    Effektiv laddning: %.0f kW  (%s, %.0f kW vid stationen)%n",
                    effKw, best.type().displayName, best.powerKw());
            if (!s.operator().isBlank())
                System.out.println("    Operatör: " + s.operator());
            if (!s.usageCost().isBlank())
                System.out.println("    Kostnad:  " + s.usageCost());
            System.out.println();
        }

        System.out.println("─".repeat(60));
        System.out.printf("Visar %d av %d kompatibla stationer (sökt inom %d km)%n",
                limit, stations.size(), Config.SEARCH_RADIUS_KM);
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
