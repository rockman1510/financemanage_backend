package com.financemanage.loan.domain.policy;

import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;

/** Interest-only installments, the whole principal is repaid with the last installment. */
public class BulletScheduleGenerator extends MonthlyScheduleGenerator {

    @Override
    public RepaymentMethod method() {
        return RepaymentMethod.BULLET;
    }

    @Override
    protected Money principalPortion(Money balance, int remainingPeriods, BigDecimal monthlyRate, Money interest) {
        return Money.zero(balance.currency());
    }
}
