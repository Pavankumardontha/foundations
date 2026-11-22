package com.zenith.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The full Money suite: every invariant guarded.
 * A value object with untested invariants is not a value object.
 */
class MoneyTest {

    // ---- invariant 1: the amount must be >= 0 ----

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-1.00", "-99999.99"})
    void rejectsNegativeAmounts(String negative) {
        assertThatThrownBy(() -> new Money(new BigDecimal(negative), "GBP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, "GBP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void acceptsZeroBecauseZeroIsNotNegative() {
        Money zero = new Money(new BigDecimal("0.00"), "GBP");

        assertEquals(new BigDecimal("0.00"), zero.amount());
    }

    // ---- invariant 2: the currency must be three uppercase letters ----

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "GB", "GBPX", "123", "gbp", "GbP", "US$"})
    void rejectsMalformedCurrencyCodes(String badCode) {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), badCode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    // ---- invariant 3: adding works, and only within one currency ----

    @Test
    void addsAmountsOfTheSameCurrency() {
        Money ten = new Money(new BigDecimal("10.00"), "GBP");
        Money twoFifty = new Money(new BigDecimal("2.50"), "GBP");

        assertEquals(new Money(new BigDecimal("12.50"), "GBP"), ten.plus(twoFifty));
    }

    @Test
    void refusesToAddDifferentCurrencies() {
        Money gbp = new Money(new BigDecimal("10.00"), "GBP");
        Money eur = new Money(new BigDecimal("10.00"), "EUR");

        assertThatThrownBy(() -> gbp.plus(eur))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
    }

    // ---- invariant 4: multiplying by a quantity works ----

    @ParameterizedTest
    @CsvSource({
            "12.50, 4,  50.00",
            "9.99,  3,  29.97",
            "0.00,  10, 0.00"
    })
    void multipliesByAQuantity(BigDecimal amount, int quantity, BigDecimal expected) {
        Money price = new Money(amount, "GBP");

        assertEquals(new Money(expected, "GBP"), price.times(quantity));
    }
}
