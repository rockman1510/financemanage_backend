package com.financemanage.saving.domain.model;

import java.time.LocalDate;

public enum ContributionFrequency {
    WEEKLY, MONTHLY;

    /** Date of contribution {@code n}, always counted from the first one (no drift on short months). */
    LocalDate add(LocalDate first, long n) {
        return this == WEEKLY ? first.plusWeeks(n) : first.plusMonths(n);
    }
}
