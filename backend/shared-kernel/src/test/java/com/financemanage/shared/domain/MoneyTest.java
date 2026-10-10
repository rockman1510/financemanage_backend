package com.financemanage.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAndSubtractsSameCurrency() {
        Money a = Money.of("100.50", "USD");
        Money b = Money.of("20.25", "USD");

        assertThat(a.plus(b)).isEqualTo(Money.of("120.75", "USD"));
        assertThat(a.minus(b)).isEqualTo(Money.of("80.25", "USD"));
    }

    @Test
    void rejectsDifferentCurrencies() {
        assertThatThrownBy(() -> Money.of(1, "USD").plus(Money.of(1, "VND")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Cannot combine");
    }

    @Test
    void roundsToCurrencyMinorUnit() {
        assertThat(Money.of("1234.5", "VND").rounded().amount()).isEqualByComparingTo("1235");
        assertThat(Money.of("10.005", "USD").rounded().amount()).isEqualByComparingTo("10.01");
    }

    @Test
    void exposesAmountsWithCurrencyScaleForApis() {
        assertThat(Money.of("100000000", "VND").roundedAmount().toString()).isEqualTo("100000000");
        assertThat(Money.of("10.005", "USD").roundedAmount().toString()).isEqualTo("10.01");
        assertThat(Percentage.of("6.5000").normalized().toString()).isEqualTo("6.5");
        assertThat(Percentage.of("10.0000").normalized().toString()).isEqualTo("10");
    }

    @Test
    void equalityIgnoresInputScale() {
        assertThat(Money.of("10", "VND")).isEqualTo(Money.of("10.0000", "VND"));
    }

    @Test
    void dividesWithCalculationScale() {
        assertThat(Money.of(100, "VND").dividedBy(3).amount()).isEqualByComparingTo("33.3333");
    }

    @Test
    void percentageConvertsToFraction() {
        assertThat(Percentage.of("6.5").asFraction()).isEqualByComparingTo(new BigDecimal("0.065"));
        assertThatThrownBy(() -> Percentage.of("-1")).isInstanceOf(DomainException.class);
    }
}
