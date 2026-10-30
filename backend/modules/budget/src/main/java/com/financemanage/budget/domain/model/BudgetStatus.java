package com.financemanage.budget.domain.model;

import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * How a budget stands on a given day. {@code dailySuggestion} = remaining / days left in the period (0 when nothing
 * is left); {@code period} is null when the budget has not started or has ended.
 */
public record BudgetStatus(PeriodRange period, Money spent, Money remaining, int percent, Money dailySuggestion,
                           long daysLeft, BudgetState state) {

    static BudgetStatus of(PeriodRange period, Money amount, Money spent, LocalDate date,
                           int warningPercent) {
        Money remaining = amount.minus(spent);
        int percent = amount.isZero() ? 100 : spent.amount().multiply(BigDecimal.valueOf(100))
                .divide(amount.amount(), 0, RoundingMode.DOWN).intValue();
        long daysLeft = Math.max(period.daysLeft(date), 1);
        Money daily = remaining.isPositive() ? remaining.dividedBy((int) daysLeft).roundedDown()
                : Money.zero(amount.currency());
        BudgetState state = percent >= 100 ? BudgetState.EXCEEDED
                : percent >= warningPercent ? BudgetState.WARNING : BudgetState.ON_TRACK;
        return new BudgetStatus(period, spent, remaining, percent, daily, daysLeft, state);
    }

    static BudgetStatus inactive(Money amount, BudgetState state) {
        Money zero = Money.zero(amount.currency());
        return new BudgetStatus(null, zero, amount, 0, zero, 0, state);
    }
}
