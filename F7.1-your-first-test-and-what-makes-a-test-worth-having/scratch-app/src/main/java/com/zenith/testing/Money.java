package com.zenith.testing;

import java.math.BigDecimal;

/**
 * Your Money record from F3.6, now carrying its amount as BigDecimal.
 * Same guarantees as before: no negative amount, a real 3-letter currency
 * code, and plus() refuses to mix currencies.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {                                   // compact constructor
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("amount must be >= 0, was " + amount);
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("ISO-4217 currency code required, was " + currency);
        }
        currency = currency.toUpperCase();           // normalise before storage
    }

    public Money plus(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "currency mismatch: cannot add " + other.currency + " to " + this.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
}
