package com.exercises;

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

        // Bug 1: Null or zero exchange rate returns NullPointerException / Div by zero
        Double rate = currency.equalsIgnoreCase("USD") ? 1.0 : rateService.getRateToUsd(currency);

        double rawSubtotal = 0.0;
        for (OrderItem item : items) {
            // Bug 2: Doesn't check if item itself is null before calling methods
            if (item.quantity() <= 0 || item.price() < 0) {
                throw new IllegalArgumentException("Invalid item pricing or quantity");
            }
            rawSubtotal += item.price() * item.quantity();
        }

        // Bug 3: Floating point precision drift on repeated discount fractions
        double discountMultiplier = 1.0;
        if ("TIER10".equalsIgnoreCase(promoCode)) {
            discountMultiplier = 0.90;
        } else if ("VIP50".equalsIgnoreCase(promoCode)) {
            discountMultiplier = 0.50;
        }

        double totalInForeignCurrency = rawSubtotal * discountMultiplier;
        double totalInUsd = totalInForeignCurrency * rate;

        return Math.round(totalInUsd * 100.0) / 100.0;
    }
}
