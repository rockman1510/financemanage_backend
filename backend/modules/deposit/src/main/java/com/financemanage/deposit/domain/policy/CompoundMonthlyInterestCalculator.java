package com.financemanage.deposit.domain.policy;

import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Interest compounded every full month; the remaining partial month accrues simple interest (actual/365).
 */
public class CompoundMonthlyInterestCalculator implements InterestCalculator {

    private final SimpleActual365InterestCalculator partialPeriod = new SimpleActual365InterestCalculator();

    @Override
    public InterestCalculationMethod method() {
        return InterestCalculationMethod.COMPOUND_MONTHLY;
    }

    @Override
    public Money interest(Money principal, Percentage annualRate, LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            return Money.zero(principal.currency());
        }
        long fullMonths = ChronoUnit.MONTHS.between(from, to);
        BigDecimal monthlyRate = annualRate.asFraction().divide(BigDecimal.valueOf(12), MathContext.DECIMAL128);
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow((int) fullMonths, MathContext.DECIMAL128);
        Money compounded = principal.times(growth);
        Money remainder = partialPeriod.interest(compounded, annualRate, from.plusMonths(fullMonths), to);
        return compounded.plus(remainder).minus(principal);
    }
}
