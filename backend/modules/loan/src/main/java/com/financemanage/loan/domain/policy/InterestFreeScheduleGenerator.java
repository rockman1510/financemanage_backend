package com.financemanage.loan.domain.policy;

import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.math.BigDecimal;

/** Personal loans without interest: principal split evenly; a 1-month term is a lump sum. */
public class InterestFreeScheduleGenerator extends MonthlyScheduleGenerator {

    @Override
    public RepaymentMethod method() {
        return RepaymentMethod.INTEREST_FREE;
    }

    @Override
    protected BigDecimal monthlyRate(Percentage annualRate) {
        return BigDecimal.ZERO;
    }

    @Override
    protected Money principalPortion(Money balance, int remainingPeriods, BigDecimal monthlyRate, Money interest) {
        return balance.dividedBy(remainingPeriods);
    }
}
