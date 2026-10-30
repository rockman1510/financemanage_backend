package com.financemanage.bill.domain.model;

import java.time.LocalDate;

public enum BillFrequency {
    WEEKLY, MONTHLY, QUARTERLY, YEARLY;

    /** Due date {@code n}, always counted from the first one (a bill due on the 31st stays on month ends). */
    LocalDate add(LocalDate first, long n) {
        return switch (this) {
            case WEEKLY -> first.plusWeeks(n);
            case MONTHLY -> first.plusMonths(n);
            case QUARTERLY -> first.plusMonths(3 * n);
            case YEARLY -> first.plusYears(n);
        };
    }
}
