package com.financemanage.bill.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRejected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Reacts to the ledger's answers and to account deletion (after commit; retried through the outbox). */
@Component
class BillListeners {

    private final BillService bills;

    BillListeners(BillService bills) {
        this.bills = bills;
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRecorded event) {
        if (BillService.SOURCE.equals(event.source())) {
            bills.paymentRecorded(event.sourceId(), event.transactionId());
        }
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRejected event) {
        if (BillService.SOURCE.equals(event.source())) {
            bills.paymentRejected(event.sourceId(), event.code() + ": " + event.message());
        }
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        bills.deleteAllOf(event.userId());
    }
}
