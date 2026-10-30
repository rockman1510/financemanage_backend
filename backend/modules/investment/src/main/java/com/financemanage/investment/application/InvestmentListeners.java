package com.financemanage.investment.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRejected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Ledger answers about the cash of trades and account deletion (after commit; retried through the outbox). */
@Component
class InvestmentListeners {

    private final InvestmentService investments;

    InvestmentListeners(InvestmentService investments) {
        this.investments = investments;
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRecorded event) {
        if (InvestmentService.SOURCE.equals(event.source())) {
            investments.cashRecorded(event.sourceId(), event.transactionId());
        }
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRejected event) {
        if (InvestmentService.SOURCE.equals(event.source())) {
            investments.cashRejected(event.sourceId());
        }
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        investments.deleteAllOf(event.userId());
    }
}
