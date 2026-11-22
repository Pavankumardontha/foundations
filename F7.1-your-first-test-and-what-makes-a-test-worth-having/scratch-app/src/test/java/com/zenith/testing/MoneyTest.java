package com.zenith.testing;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void addsTwoAmountsInTheSameCurrency() {
        Money ten = new Money(new BigDecimal("10.00"), "GBP");
        Money five = new Money(new BigDecimal("5.00"), "GBP");

        Money total = ten.plus(five);

        assertThat(total.amount()).isEqualByComparingTo("15.00");
    }

    @Test
    void normalisesCurrencyToUpperCase() {
        Money m = new Money(new BigDecimal("1.00"), "gbp");

        assertThat(m.currency()).isEqualTo("GBP");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "GBP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount must be >= 0");
    }

    @Test
    @DisplayName("refuses to add GBP to EUR")
    void refusesToAddDifferentCurrencies() {
        Money pounds = new Money(new BigDecimal("10.00"), "GBP");
        Money euros = new Money(new BigDecimal("5.00"), "EUR");

        assertThatThrownBy(() -> pounds.plus(euros))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency mismatch");
    }
}
