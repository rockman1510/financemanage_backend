package com.financemanage.budget.domain.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** One budget period, both ends inclusive. */
public record PeriodRange(LocalDate start, LocalDate end) {

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /** Days from {@code date} to the end, both included (1 on the last day). */
    public long daysLeft(LocalDate date) {
        return ChronoUnit.DAYS.between(date, end) + 1;
    }
}
