package com.financemanage.budget.application;

import com.financemanage.budget.domain.model.BudgetPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Input for a new budget. {@code startDate} defaults to the natural start of the current period (Monday, the 1st…);
 * {@code currency} defaults to VND; empty {@code categoryIds} = every expense category.
 */
public record BudgetInput(String name, BigDecimal amount, String currency, Set<UUID> categoryIds, UUID walletId,
                          BudgetPeriod period, LocalDate startDate, LocalDate endDate, Boolean repeat) {
}
