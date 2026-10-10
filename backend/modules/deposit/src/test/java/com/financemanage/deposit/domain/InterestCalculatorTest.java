package com.financemanage.deposit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.deposit.domain.policy.CompoundMonthlyInterestCalculator;
import com.financemanage.deposit.domain.policy.InterestCalculatorRegistry;
import com.financemanage.deposit.domain.policy.Simple30360InterestCalculator;
import com.financemanage.deposit.domain.policy.SimpleActual365InterestCalculator;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterestCalculatorTest {

    private static final Money PRINCIPAL = Money.of(100_000_000, "VND");
    private static final LocalDate JAN_1 = LocalDate.of(2025, 1, 1);
    private static final LocalDate JUL_1 = LocalDate.of(2025, 7, 1);

    @Test
    void simpleActual365UsesActualDays() {
        Money interest = new SimpleActual365InterestCalculator().interest(PRINCIPAL, Percentage.of("6"), JAN_1, JUL_1);
        // 100,000,000 × 6% × 181 / 365
        assertThat(interest.rounded().amount()).isEqualByComparingTo("2975342");
    }

    @Test
    void simple30360CountsThirtyDayMonths() {
        Money interest = new Simple30360InterestCalculator().interest(PRINCIPAL, Percentage.of("6"), JAN_1, JUL_1);
        assertThat(interest.rounded().amount()).isEqualByComparingTo("3000000");
    }

    @Test
    void compoundMonthlyCompoundsFullMonths() {
        Money interest = new CompoundMonthlyInterestCalculator()
                .interest(PRINCIPAL, Percentage.of("12"), JAN_1, JAN_1.plusYears(1));
        // 100,000,000 × (1.01^12 − 1)
        assertThat(interest.rounded().amount()).isEqualByComparingTo("12682503");
    }

    @Test
    void returnsZeroForEmptyOrNegativePeriods() {
        assertThat(new SimpleActual365InterestCalculator().interest(PRINCIPAL, Percentage.of("6"), JUL_1, JAN_1)
                .isZero()).isTrue();
        assertThat(new CompoundMonthlyInterestCalculator().interest(PRINCIPAL, Percentage.of("6"), JAN_1, JAN_1)
                .isZero()).isTrue();
    }

    @Test
    void registryResolvesStrategiesAndRejectsUnknown() {
        var registry = new InterestCalculatorRegistry(List.of(new SimpleActual365InterestCalculator()));

        assertThat(registry.get(InterestCalculationMethod.SIMPLE_ACTUAL_365))
                .isInstanceOf(SimpleActual365InterestCalculator.class);
        assertThatThrownBy(() -> registry.get(InterestCalculationMethod.COMPOUND_MONTHLY))
                .isInstanceOf(DomainException.class);
    }
}
