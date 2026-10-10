package com.financemanage.deposit.domain.policy;

import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** interest = principal × rate × actualDays / 365 */
public class SimpleActual365InterestCalculator implements InterestCalculator {

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

    @Override
    public InterestCalculationMethod method() {
        return InterestCalculationMethod.SIMPLE_ACTUAL_365;
    }

    @Override
    public Money interest(Money principal, Percentage annualRate, LocalDate from, LocalDate to) {
        long days = Math.max(0, ChronoUnit.DAYS.between(from, to));
        BigDecimal factor = annualRate.asFraction()
                .multiply(BigDecimal.valueOf(days))
                .divide(DAYS_IN_YEAR, MathContext.DECIMAL128);
        return principal.times(factor);
    }
}
