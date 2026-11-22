package com.zenith.money;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingRuleTest {

    @ParameterizedTest
    @CsvSource({
            "1,  50.00",
            "5,  45.00",
            "20, 40.00"
    })
    void pricesEachQuantityTier(int quantity, BigDecimal expectedPrice) {
        PricingRule rule = new PricingRule();

        Money price = rule.pricePerTicket(quantity);

        assertEquals(new Money(expectedPrice, "GBP"), price);
    }
}
