package com.zenith.money;

import java.math.BigDecimal;

/**
 * Tiered ticket pricing: the more tickets you buy, the cheaper each one gets.
 *
 *   1 to 4 tickets   -> 50.00 GBP each
 *   5 to 19 tickets  -> 45.00 GBP each
 *   20 or more       -> 40.00 GBP each
 */
public class PricingRule {

    public Money pricePerTicket(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1, was " + quantity);
        }
        if (quantity >= 20) {
            return new Money(new BigDecimal("40.00"), "GBP");
        }
        if (quantity >= 5) {
            return new Money(new BigDecimal("45.00"), "GBP");
        }
        return new Money(new BigDecimal("50.00"), "GBP");
    }
}
