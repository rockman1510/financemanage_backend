package com.financemanage.loan.domain.policy;

import com.financemanage.loan.domain.model.Installment;
import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.shared.domain.Percentage;
import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Strategy that builds a monthly repayment schedule. Implement it (and register a bean) to support a new
 * repayment method without touching the loan aggregate.
 */
public interface RepaymentScheduleGenerator {

    RepaymentMethod method();

    /**
     * @param balance       principal to amortize
     * @param periods       number of monthly installments
     * @param periodStart   start of the first period; installment k is due {@code periodStart + k months}
     * @param firstPeriodNo number given to the first generated installment
     * @param rateAt        annual rate applicable for a period starting at the given date
     */
    List<Installment> generate(Money balance, int periods, LocalDate periodStart, int firstPeriodNo,
                               Function<LocalDate, Percentage> rateAt);
}
