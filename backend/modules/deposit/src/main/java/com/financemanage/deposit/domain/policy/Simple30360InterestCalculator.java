package com.financemanage.deposit.domain.policy;

import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/** interest = principal × rate × days(30/360) / 360 */
public class Simple30360InterestCalculator implements InterestCalculator {

    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(360);

    @Override
    public InterestCalculationMethod method() {
        return InterestCalculationMethod.SIMPLE_30_360;
    }

    @Override
    public Money interest(Money principal, Percentage annualRate, LocalDate from, LocalDate to) {
        if (!to.isAfter(from)) {
            return Money.zero(principal.currency());
        }
        BigDecimal factor = annualRate.asFraction()
                .multiply(BigDecimal.valueOf(days30360(from, to)))
                .divide(DAYS_IN_YEAR, MathContext.DECIMAL128);
        return principal.times(factor);
    }

    static long days30360(LocalDate from, LocalDate to) {
        int d1 = Math.min(from.getDayOfMonth(), 30);
        int d2 = to.getDayOfMonth();
        if (d2 == 31 && d1 == 30) {
            d2 = 30;
        }
        return 360L * (to.getYear() - from.getYear()) + 30L * (to.getMonthValue() - from.getMonthValue()) + (d2 - d1);
    }
}
