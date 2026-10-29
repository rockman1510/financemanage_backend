package com.financemanage.currency.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.shared.domain.DomainException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExchangeRateTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency VND = Currency.getInstance("VND");
    private static final LocalDate DAY = LocalDate.of(2025, 3, 1);

    @Test
    void computesInverseRate() {
        ExchangeRate rate = ExchangeRate.manual(UUID.randomUUID(), USD, VND, new BigDecimal("25000"), DAY);

        assertThat(rate.inverse()).isEqualByComparingTo("0.00004");
        assertThat(rate.isGlobal()).isFalse();
        assertThat(rate.getSource()).isEqualTo(ExchangeRate.MANUAL_SOURCE);
    }

    @Test
    void rejectsSameCurrencyAndNonPositiveRates() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> ExchangeRate.manual(owner, USD, USD, BigDecimal.ONE, DAY))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("SAME_CURRENCY");
        assertThatThrownBy(() -> ExchangeRate.manual(owner, USD, VND, BigDecimal.ZERO, DAY))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_RATE");
    }

    @Test
    void globalRatesHaveNoOwner() {
        ExchangeRate rate = ExchangeRate.global("VCB", USD, VND, new BigDecimal("25400"), DAY);

        assertThat(rate.isGlobal()).isTrue();
        assertThat(rate.getSource()).isEqualTo("VCB");
    }
}
