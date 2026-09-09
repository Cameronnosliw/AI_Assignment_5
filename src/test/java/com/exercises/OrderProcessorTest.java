package com.exercises;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderProcessorTest {

    @Mock
    private ExchangeRateService rateService;

    private OrderProcessor orderProcessor;

    @BeforeEach
    void setUp() {
        orderProcessor = new OrderProcessor(rateService);
    }

    @Test
    void calculateTotalInUsd_usdSkipsRateServiceAndSumsItems() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 10.00, 2),
                new OrderProcessor.OrderItem("SKU-2", 5.50, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "USD", null);

        assertEquals(25.50, total, 0.0001);
        verifyNoInteractions(rateService);
    }

    @Test
    void calculateTotalInUsd_usdIsCaseInsensitive() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 10.00, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "usd", null);

        assertEquals(10.00, total, 0.0001);
        verify(rateService, never()).getRateToUsd("usd");
    }

    @Test
    void calculateTotalInUsd_convertsForeignCurrencyUsingRateService() {
        when(rateService.getRateToUsd("GBP")).thenReturn(1.25);
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 10.00, 2)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "GBP", null);

        assertEquals(25.00, total, 0.0001);
        verify(rateService).getRateToUsd("GBP");
    }

    @Test
    void calculateTotalInUsd_appliesTier10Discount() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 100.00, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "USD", "TIER10");

        assertEquals(90.00, total, 0.0001);
    }

    @Test
    void calculateTotalInUsd_appliesVip50Discount() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 80.00, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "USD", "VIP50");

        assertEquals(40.00, total, 0.0001);
    }

    @Test
    void calculateTotalInUsd_promoCodesAreCaseInsensitive() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 100.00, 1)
        );

        assertEquals(90.00, orderProcessor.calculateTotalInUsd(items, "USD", "tier10"), 0.0001);
        assertEquals(50.00, orderProcessor.calculateTotalInUsd(items, "USD", "vip50"), 0.0001);
    }

    @Test
    void calculateTotalInUsd_unknownPromoCodeAppliesNoDiscount() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 20.00, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "USD", "UNKNOWN");

        assertEquals(20.00, total, 0.0001);
    }

    @Test
    void calculateTotalInUsd_appliesDiscountBeforeCurrencyConversion() {
        when(rateService.getRateToUsd("EUR")).thenReturn(2.0);
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 100.00, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "EUR", "TIER10");

        assertEquals(180.00, total, 0.0001);
    }

    @Test
    void calculateTotalInUsd_roundsHalfUpToTwoDecimalPlaces() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 2.675, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "USD", null);

        assertEquals(2.68, total, 0.0001);
    }

    @Test
    void calculateTotalInUsd_roundsHalfUpAfterConversion() {
        when(rateService.getRateToUsd("JPY")).thenReturn(0.0075);
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 100.00, 1)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "JPY", null);

        assertEquals(0.75, total, 0.0001);
    }

    @Test
    void calculateTotalInUsd_throwsWhenRateServiceReturnsNull() {
        when(rateService.getRateToUsd("XYZ")).thenReturn(null);
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 10.00, 1)
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(items, "XYZ", null)
        );
        assertTrue(ex.getMessage().contains("XYZ"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.5})
    void calculateTotalInUsd_throwsWhenRateIsNotPositive(double rate) {
        when(rateService.getRateToUsd("CAD")).thenReturn(rate);
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 10.00, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(items, "CAD", null)
        );
    }

    @Test
    void calculateTotalInUsd_throwsWhenItemsIsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(null, "USD", null)
        );
        verifyNoInteractions(rateService);
    }

    @Test
    void calculateTotalInUsd_throwsWhenItemsIsEmpty() {
        assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(Collections.emptyList(), "USD", null)
        );
        verifyNoInteractions(rateService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void calculateTotalInUsd_throwsWhenCurrencyIsNullOrBlank(String currency) {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 10.00, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(items, currency, null)
        );
        verifyNoInteractions(rateService);
    }

    @Test
    void calculateTotalInUsd_throwsWhenAnItemIsNull() {
        List<OrderProcessor.OrderItem> items = Arrays.asList(
                new OrderProcessor.OrderItem("SKU-1", 10.00, 1),
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(items, "USD", null)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void calculateTotalInUsd_throwsWhenQuantityIsNotPositive(int quantity) {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 10.00, quantity)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(items, "USD", null)
        );
    }

    @Test
    void calculateTotalInUsd_throwsWhenPriceIsNegative() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", -0.01, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orderProcessor.calculateTotalInUsd(items, "USD", null)
        );
    }

    @Test
    void calculateTotalInUsd_allowsZeroPrice() {
        List<OrderProcessor.OrderItem> items = List.of(
                new OrderProcessor.OrderItem("SKU-1", 0.00, 3)
        );

        double total = orderProcessor.calculateTotalInUsd(items, "USD", "VIP50");

        assertEquals(0.00, total, 0.0001);
    }
}
