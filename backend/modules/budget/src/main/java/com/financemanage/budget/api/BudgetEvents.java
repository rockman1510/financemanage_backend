package com.financemanage.budget.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events published by the budget module. Primitive payloads keep them easy to serialize (Kafka-ready). */
public final class BudgetEvents {

    private BudgetEvents() {
    }

    /**
     * Spending in the current period reached {@code percent} of the budget: the warning level (80 by default) or 100
     * (exceeded). Published once per level and period.
     */
    public record BudgetThresholdReached(UUID budgetId, UUID ownerId, String name, int percent, BigDecimal spent,
                                         BigDecimal amount, String currency, LocalDate periodStart,
                                         LocalDate periodEnd) {
    }
}
