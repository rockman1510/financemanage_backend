package com.financemanage.ledger.domain.model;

import java.time.LocalDate;

public enum Frequency {
    DAILY, WEEKLY, MONTHLY, YEARLY;

    /**
     * The date {@code periods} periods after {@code start}. Always computed from the start date, so a monthly rule
     * starting on the 31st falls on the last day of shorter months and returns to the 31st afterwards.
     */
    public LocalDate add(LocalDate start, long periods) {
        return switch (this) {
            case DAILY -> start.plusDays(periods);
            case WEEKLY -> start.plusWeeks(periods);
            case MONTHLY -> start.plusMonths(periods);
            case YEARLY -> start.plusYears(periods);
        };
    }
}
