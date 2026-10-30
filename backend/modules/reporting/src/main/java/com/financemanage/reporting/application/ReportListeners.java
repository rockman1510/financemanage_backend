package com.financemanage.reporting.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.ledger.api.LedgerEvents.TransactionDeleted;
import com.financemanage.ledger.api.LedgerEvents.TransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.TransactionUpdated;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Feeds the read model from ledger events (after commit; retried through the outbox) and deletes accounts. */
@Component
class ReportListeners {

    private final ReportProjection projection;
    private final NetWorthCalculator netWorth;

    ReportListeners(ReportProjection projection, NetWorthCalculator netWorth) {
        this.projection = projection;
        this.netWorth = netWorth;
    }

    @ApplicationModuleListener
    void on(TransactionRecorded event) {
        projection.recorded(event.transaction());
    }

    @ApplicationModuleListener
    void on(TransactionUpdated event) {
        projection.updated(event.after());
    }

    @ApplicationModuleListener
    void on(TransactionDeleted event) {
        projection.deleted(event.transaction());
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        projection.deleteAllOf(event.userId());
        netWorth.evict(event.userId());
    }
}
