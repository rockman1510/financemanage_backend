package com.financemanage.loan.domain.policy;

import com.financemanage.loan.domain.model.Installment;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Template method for monthly schedules on a declining balance. Interest of a period is
 * {@code balance × annualRate / 12}; subclasses only decide how much principal is repaid each period.
 * The last installment always clears the remaining balance, absorbing rounding differences.
 */
public abstract class MonthlyScheduleGenerator implements RepaymentScheduleGenerator {

    private static final BigDecimal MONTHS = BigDecimal.valueOf(12);

    @Override
    public List<Installment> generate(Money balance, int periods, LocalDate periodStart, int firstPeriodNo,
                                      Function<LocalDate, Percentage> rateAt) {
        List<Installment> installments = new ArrayList<>(periods);
        Money remaining = balance;
        for (int i = 0; i < periods; i++) {
            LocalDate start = periodStart.plusMonths(i);
            BigDecimal monthlyRate = monthlyRate(rateAt.apply(start));
            Money interest = remaining.times(monthlyRate).rounded();
            int remainingPeriods = periods - i;
            Money principal = remainingPeriods == 1
                    ? remaining
                    : principalPortion(remaining, remainingPeriods, monthlyRate, interest).roundedDown().min(remaining);
            installments.add(Installment.planned(firstPeriodNo + i, periodStart.plusMonths(i + 1L), principal,
                    interest));
            remaining = remaining.minus(principal);
        }
        return installments;
    }

    protected BigDecimal monthlyRate(Percentage annualRate) {
        return annualRate.asFraction().divide(MONTHS, MathContext.DECIMAL128);
    }

    /** Principal repaid in the current period. */
    protected abstract Money principalPortion(Money balance, int remainingPeriods, BigDecimal monthlyRate,
                                              Money interest);
}
