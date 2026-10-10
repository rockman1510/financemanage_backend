package com.financemanage.loan.domain.policy;

import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;

/** Dư nợ giảm dần: the same principal each month, interest decreases over time. */
public class EqualPrincipalScheduleGenerator extends MonthlyScheduleGenerator {

    @Override
    public RepaymentMethod method() {
        return RepaymentMethod.EQUAL_PRINCIPAL;
    }

    @Override
    protected Money principalPortion(Money balance, int remainingPeriods, BigDecimal monthlyRate, Money interest) {
        return balance.dividedBy(remainingPeriods);
    }
}
