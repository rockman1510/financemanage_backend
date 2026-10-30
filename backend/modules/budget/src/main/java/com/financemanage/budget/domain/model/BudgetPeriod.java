package com.financemanage.budget.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** Length of a budget period. A CUSTOM budget has one period from its start date to its end date. */
public enum BudgetPeriod {
    WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM;

    /** Start of period number {@code n}, always counted from the first start date (no drift on short months). */
    LocalDate start(LocalDate first, long n) {
        return switch (this) {
            case WEEKLY -> first.plusWeeks(n);
            case MONTHLY -> first.plusMonths(n);
            case QUARTERLY -> first.plusMonths(3 * n);
            case YEARLY -> first.plusYears(n);
            case CUSTOM -> throw new IllegalStateException("A custom budget has a single period");
        };
    }

    /** Rough number of whole periods between two dates, refined by the caller. */
    long estimate(LocalDate first, LocalDate date) {
        return switch (this) {
            case WEEKLY -> ChronoUnit.WEEKS.between(first, date);
            case MONTHLY -> ChronoUnit.MONTHS.between(first, date);
            case QUARTERLY -> ChronoUnit.MONTHS.between(first, date) / 3;
            case YEARLY -> ChronoUnit.YEARS.between(first, date);
            case CUSTOM -> 0;
        };
    }

    /** Natural start of the period containing {@code date}: Monday, the 1st, the quarter's first day, January 1st. */
    public LocalDate naturalStart(LocalDate date) {
        return switch (this) {
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
            case QUARTERLY -> date.withDayOfMonth(1).withMonth(((date.getMonthValue() - 1) / 3) * 3 + 1);
            case YEARLY -> date.withDayOfYear(1);
            case CUSTOM -> date;
        };
    }
}
