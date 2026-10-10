package com.financemanage.loan.domain.policy;

import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Equal total payment each month: {@code payment = balance × r / (1 − (1 + r)^−n)}.
 * Recomputed every period, so the payment adjusts automatically after a rate change or prepayment.
 */
public class AnnuityScheduleGenerator extends MonthlyScheduleGenerator {

    @Override
    public RepaymentMethod method() {
        return RepaymentMethod.ANNUITY;
    }

    @Override
    protected Money principalPortion(Money balance, int remainingPeriods, BigDecimal monthlyRate, Money interest) {
        if (monthlyRate.signum() == 0) {
            return balance.dividedBy(remainingPeriods);
        }
        BigDecimal discount = BigDecimal.ONE.add(monthlyRate).pow(-remainingPeriods, MathContext.DECIMAL128);
        BigDecimal factor = monthlyRate.divide(BigDecimal.ONE.subtract(discount), MathContext.DECIMAL128);
        Money payment = balance.times(factor).rounded();
        return payment.minus(interest);
    }
}
