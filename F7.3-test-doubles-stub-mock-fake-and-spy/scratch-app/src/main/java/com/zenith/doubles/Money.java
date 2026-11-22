package com.zenith.doubles;

/**
 * A value object: defined entirely by its value, cheap to create,
 * connected to nothing outside. Amounts are in paise.
 * Value objects are used directly in tests — never doubled.
 */
public record Money(long paise) {

    public Money plus(Money other) {
        return new Money(paise + other.paise);
    }
}
