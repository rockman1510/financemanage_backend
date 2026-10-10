package com.financemanage.loan.domain.model;

import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;

/** Annual rate effective from a date (supports floating-rate loans). */
public record RatePeriod(LocalDate effectiveFrom, Percentage annualRate) {
}
