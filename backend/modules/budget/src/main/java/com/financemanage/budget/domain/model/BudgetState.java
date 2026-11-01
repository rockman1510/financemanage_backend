package com.financemanage.budget.domain.model;

public enum BudgetState {
    /** Before the first period */
    NOT_STARTED,
    ON_TRACK,
    /** Spending reached the warning level (80% by default) */
    WARNING,
    EXCEEDED,
    /** A custom or non-repeating budget whose period is over */
    ENDED
}
