package se.elitrobban.elbilsladdning.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Approximate DC prices per major Swedish charging network,
 * sourced from each operator's public pricing page (utan abonnemang/roaming).
 * Updated 2026-06-13. Always shown with a disclaimer to check the operator's app.
 */
@Service
public class OperatorPriceService {

    // Order matters — first matching entry wins (more specific names first)
    private static final LinkedHashMap<String, String> PRICES = new LinkedHashMap<>();

    static {
        PRICES.put("ionity",         "~6,96 kr/kWh");
        PRICES.put("allego",         "~6,50 kr/kWh");
        PRICES.put("eviny",          "~3,99 kr/kWh");
        PRICES.put("nima",           "~5,99 kr/kWh");
        PRICES.put("northe",         "~5,20 kr/kWh");
        PRICES.put("hedin",          "~5,20 kr/kWh");
        PRICES.put("kungsbacka volvo","~6,35 kr/kWh");
        PRICES.put("tesla",          "~4,50 kr/kWh");
        PRICES.put("vattenfall",  "~3,49 kr/kWh");
        PRICES.put("incharge",    "~3,49 kr/kWh");
        PRICES.put("recharge",    "~3,49 kr/kWh");
        PRICES.put("circle k",    "~5,99 kr/kWh");
        PRICES.put("circlek",     "~5,99 kr/kWh");
        PRICES.put("bee",         "~3,29 kr/kWh");
        PRICES.put("mer",         "~6,24 kr/kWh");
        PRICES.put("e.on",        "~4,75 kr/kWh");
        PRICES.put("eon",         "~4,75 kr/kWh");
        PRICES.put("clever",      "~3,99 kr/kWh");
        PRICES.put("chargenode",            "~5,00 kr/kWh");
        PRICES.put("p-hus kungsgatan 6",    "~5,00 kr/kWh");
        PRICES.put("kungsmässan",           "~5,00 kr/kWh");
        PRICES.put("bissmarksgatan",        "~4,75 kr/kWh");
        PRICES.put("borgmästaregatan",      "~4,75 kr/kWh");
        PRICES.put("lidl",                "~2,99 kr/kWh");
        PRICES.put("ikea",                "Gratis (för kunder)");
        PRICES.put("preem",               "~3,49 kr/kWh");
        PRICES.put("st1",                 "~3,49 kr/kWh");
    }

    /**
     * Returns an approximate price by matching operator name, then station name as fallback.
     * Returns null if neither matches a known network.
     */
    public String getApproxPrice(String operator, String stationName) {
        String price = matchIn(operator);
        if (price == null) price = matchIn(stationName);
        return price;
    }

    private String matchIn(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase();
        // Skip generic OCM placeholder
        if (lower.contains("unknown operator")) return null;
        for (Map.Entry<String, String> e : PRICES.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    /**
     * Parses a price label like "~6,96 kr/kWh" to 6.96.
     * Returns null for non-numeric entries such as "Gratis (för kunder)".
     */
    public Double parseKr(String priceLabel) {
        if (priceLabel == null || priceLabel.isBlank()) return null;
        var m = java.util.regex.Pattern.compile("(\\d+[.,]?\\d*)").matcher(priceLabel);
        if (!m.find()) return null;
        return Double.parseDouble(m.group(1).replace(",", "."));
    }

    /**
     * Average kr/kWh across the table, rounded to 2 decimals. Alias keys for the
     * same network ("circle k"/"circlek", "e.on"/"eon") count once; free/non-numeric
     * entries are excluded.
     */
    public double nationalAverageKr() {
        var seen = new java.util.HashSet<String>();
        double sum = 0;
        int n = 0;
        for (Map.Entry<String, String> e : PRICES.entrySet()) {
            if (!seen.add(e.getKey().replaceAll("[^a-z0-9]", ""))) continue;
            Double kr = parseKr(e.getValue());
            if (kr == null) continue;
            sum += kr;
            n++;
        }
        return n == 0 ? 0 : Math.round(sum / n * 100.0) / 100.0;
    }
}
