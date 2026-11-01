package com.financemanage.budget.application;

import com.financemanage.budget.domain.model.Budget;
import com.financemanage.budget.domain.model.BudgetStatus;
import java.util.Set;

/**
 * A budget and its status on a day. Spending in a currency without exchange rate to the budget's currency is left
 * out and the currency is listed in {@code unconvertedCurrencies}.
 */
public record BudgetView(Budget budget, BudgetStatus status, Set<String> unconvertedCurrencies) {
}
