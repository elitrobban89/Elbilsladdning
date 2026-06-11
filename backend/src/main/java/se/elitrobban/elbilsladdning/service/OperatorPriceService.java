package se.elitrobban.elbilsladdning.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Approximate DC prices per major Swedish charging network,
 * sourced from each operator's public pricing page.
 * Updated 2026-06. Always shown with a disclaimer to check the operator's app.
 */
@Service
public class OperatorPriceService {

    // Order matters — first matching entry wins (more specific names first)
    private static final LinkedHashMap<String, String> PRICES = new LinkedHashMap<>();

    static {
        PRICES.put("ionity",      "~0,79 EUR/kWh");
        PRICES.put("allego",      "~0,79 EUR/kWh");
        PRICES.put("eviny",       "~3,99 kr/kWh");
        PRICES.put("tesla",       "~4,50 kr/kWh");
        PRICES.put("vattenfall",  "~3,49 kr/kWh");
        PRICES.put("incharge",    "~3,49 kr/kWh");
        PRICES.put("recharge",    "~3,49 kr/kWh");
        PRICES.put("circle k",    "~3,99 kr/kWh");
        PRICES.put("circlek",     "~3,99 kr/kWh");
        PRICES.put("bee",         "~3,29 kr/kWh");
        PRICES.put("mer",         "~3,49 kr/kWh");
        PRICES.put("e.on",        "~3,29 kr/kWh");
        PRICES.put("eon",         "~3,29 kr/kWh");
        PRICES.put("clever",      "~3,99 kr/kWh");
        PRICES.put("chargenode",            "~5,00 kr/kWh");
        PRICES.put("p-hus kungsgatan 6",    "~5,00 kr/kWh");
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
}
