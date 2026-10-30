package com.financemanage.saving.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRejected;
import com.financemanage.ledger.api.LedgerEvents.TransactionRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Ledger answers, round-ups of new expenses and account deletion (after commit; retried through the outbox). */
@Component
class SavingListeners {

    private final SavingService savings;

    SavingListeners(SavingService savings) {
        this.savings = savings;
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRecorded event) {
        if (SavingService.SOURCE.equals(event.source())) {
            savings.movementRecorded(event.sourceId(), event.transactionId());
        }
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRejected event) {
        if (SavingService.SOURCE.equals(event.source())) {
            savings.movementRejected(event.sourceId(), event.code() + ": " + event.message());
        }
    }

    /** Only the user's own expenses are rounded up (not bill payments or other module requests). */
    @ApplicationModuleListener
    void on(TransactionRecorded event) {
        if ("EXPENSE".equals(event.transaction().type()) && event.transaction().source() == null) {
            savings.onExpense(event.transaction());
        }
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        savings.deleteAllOf(event.userId());
    }
}
