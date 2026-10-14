package com.payflow.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void arithmeticReturnsNewValuesAndLeavesOriginalUnchanged() {
        Money ten = Money.of(1000, "INR");
        Money result = ten.plus(Money.of(250, "INR")).minus(Money.of(50, "INR"));
        assertThat(result).isEqualTo(Money.of(1200, "INR"));
        assertThat(ten.minor()).isEqualTo(1000);
    }

    @Test
    void equalValuesAreEqualAndHashEqually() {
        Set<Money> set = new HashSet<>(List.of(Money.of(500, "INR"), Money.of(500, "INR")));
        assertThat(set).hasSize(1);
        assertThat(Money.of(500, "INR").hashCode()).isEqualTo(Money.of(500, "INR").hashCode());
        assertThat(Money.of(500, "INR")).isNotEqualTo(Money.of(500, "USD"));
    }

    @Test
    void naturalOrderSortsByAmount() {
        List<Money> values = new ArrayList<>(List.of(Money.of(300, "INR"), Money.of(-5, "INR"), Money.of(100, "INR")));
        values.sort(null);
        assertThat(values).extracting(Money::minor).containsExactly(-5L, 100L, 300L);
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of(1, "INR").plus(Money.of(1, "USD"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(1, "RUPEE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowIsDetectedInsteadOfWrappingAround() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "INR").plus(Money.of(1, "INR"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void formatsMinorUnits() {
        assertThat(Money.of(123456, "INR")).hasToString("INR 1234.56");
        assertThat(Money.of(5, "INR")).hasToString("INR 0.05");
        assertThat(Money.of(-50, "INR")).hasToString("INR -0.50");
    }
}
