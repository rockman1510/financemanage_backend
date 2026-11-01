package com.financemanage.budget.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.ledger.api.LedgerEvents.TransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerEvents.TransactionUpdated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Reacts to ledger and identity events (after their transaction commits; retried through the outbox). */
@Component
class BudgetListeners {

    private final BudgetService budgets;

    BudgetListeners(BudgetService budgets) {
        this.budgets = budgets;
    }

    @ApplicationModuleListener
    void on(TransactionRecorded event) {
        checkExpense(event.transaction());
    }

    @ApplicationModuleListener
    void on(TransactionUpdated event) {
        checkExpense(event.after());
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        budgets.deleteAllOf(event.userId());
    }

    private void checkExpense(TransactionSnapshot transaction) {
        if ("EXPENSE".equals(transaction.type()) && !transaction.excludeFromReport()) {
            budgets.onExpense(transaction);
        }
    }
}
