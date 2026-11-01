package com.financemanage.ledger.application;

import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRejected;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.shared.domain.DomainException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records the movements other modules ask for and answers with the outcome. Runs after the requester's transaction
 * commits and is retried through the outbox; the request id makes it idempotent.
 */
@Component
class ExternalTransactionListener {

    private final TransactionService transactions;
    private final ApplicationEventPublisher events;

    ExternalTransactionListener(TransactionService transactions, ApplicationEventPublisher events) {
        this.transactions = transactions;
        this.events = events;
    }

    @ApplicationModuleListener
    void on(RecordExternalTransaction command) {
        try {
            Transaction recorded = transactions.recordExternal(command);
            events.publishEvent(new ExternalTransactionRecorded(command.requestId(), command.source(),
                    command.sourceId(), command.ownerId(), recorded.getId()));
        } catch (DomainException e) {
            events.publishEvent(new ExternalTransactionRejected(command.requestId(), command.source(),
                    command.sourceId(), command.ownerId(), e.getCode(), e.getMessage()));
        }
    }
}
