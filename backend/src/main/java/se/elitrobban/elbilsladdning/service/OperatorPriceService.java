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
        PRICES.put("lidl",        "~2,99 kr/kWh");
        PRICES.put("ikea",        "Gratis (för kunder)");
        PRICES.put("preem",       "~3,49 kr/kWh");
        PRICES.put("st1",         "~3,49 kr/kWh");
    }

    /** Returns an approximate price string like "~3,49 kr/kWh", or null if operator is unknown. */
    public String getApproxPrice(String operator) {
        if (operator == null || operator.isBlank()) return null;
        String lower = operator.toLowerCase();
        for (Map.Entry<String, String> e : PRICES.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return null;
    }
}
