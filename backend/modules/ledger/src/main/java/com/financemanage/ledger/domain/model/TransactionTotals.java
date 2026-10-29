package com.financemanage.ledger.domain.model;

import com.financemanage.shared.domain.Money;

/** Income and expense of one currency, excluding transfers, adjustments and transactions excluded from reports. */
public record TransactionTotals(Money income, Money expense) {

    public Money net() {
        return income.minus(expense);
    }
}
