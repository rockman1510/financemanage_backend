package com.financemanage.deposit.domain.policy;

import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;

/**
 * Strategy for computing interest. Add a new way of computing interest by implementing this interface
 * and registering it as a bean – no existing code needs to change.
 */
public interface InterestCalculator {

    InterestCalculationMethod method();

    /** Interest earned on {@code principal} between {@code from} (inclusive) and {@code to} (exclusive), unrounded. */
    Money interest(Money principal, Percentage annualRate, LocalDate from, LocalDate to);
}
