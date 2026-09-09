package com.exercises;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class OrderProcessor {

    private final ExchangeRateService rateService;

    public record OrderItem(String sku, double price, int quantity) {}

    public OrderProcessor(ExchangeRateService rateService) {
        this.rateService = rateService;
    }

    public double calculateTotalInUsd(List<OrderItem> items, String currency, String promoCode) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items list cannot be empty");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or blank");
        }

        // Bug fix: the original code used `Double rate = cond ? 1.0 : rateService.getRateToUsd(currency);`
        // The ternary operator forces numeric promotion of the Double branch to a primitive double,
        // which triggers an immediate, confusing NullPointerException whenever getRateToUsd(...)
        // returns null (e.g. for an unsupported currency) -- even before the value is ever used.
        // We now fetch the rate explicitly and turn an unsupported currency into a clear,
        // caller-friendly IllegalArgumentException instead of an NPE.
        double rate;
        if (currency.equalsIgnoreCase("USD")) {
            rate = 1.0;
        } else {
            Double fetchedRate = rateService.getRateToUsd(currency);
            if (fetchedRate == null) {
                throw new IllegalArgumentException("Unsupported currency: " + currency);
            }
            if (fetchedRate <= 0) {
                throw new IllegalArgumentException("Exchange rate must be positive for currency: " + currency);
            }
            rate = fetchedRate;
        }

        // Bug fix: summing/multiplying with primitive doubles and then rounding via
        // Math.round(x * 100.0) / 100.0 is a classic source of off-by-one-cent errors
        // (e.g. 2.675 is stored as 2.6749999999999998... in binary floating point, so the
        // old code rounded it down to 2.67 instead of the correct 2.68). We use BigDecimal,
        // seeded from Double.toString() via BigDecimal.valueOf(...), so decimal amounts like
        // 2.675 are represented exactly and rounded predictably.
        BigDecimal rawSubtotal = BigDecimal.ZERO;
        for (OrderItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("Order item cannot be null");
            }
            if (item.quantity() <= 0 || item.price() < 0) {
                throw new IllegalArgumentException("Invalid item pricing or quantity");
            }
            BigDecimal itemPrice = BigDecimal.valueOf(item.price());
            BigDecimal itemQuantity = BigDecimal.valueOf(item.quantity());
            rawSubtotal = rawSubtotal.add(itemPrice.multiply(itemQuantity));
        }

        BigDecimal discountMultiplier;
        if ("TIER10".equalsIgnoreCase(promoCode)) {
            discountMultiplier = new BigDecimal("0.90");
        } else if ("VIP50".equalsIgnoreCase(promoCode)) {
            discountMultiplier = new BigDecimal("0.50");
        } else {
            discountMultiplier = BigDecimal.ONE;
        }

        BigDecimal totalInForeignCurrency = rawSubtotal.multiply(discountMultiplier);
        BigDecimal totalInUsd = totalInForeignCurrency.multiply(BigDecimal.valueOf(rate));

        return totalInUsd.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
