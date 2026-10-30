package com.financemanage.deposit.application;

import com.financemanage.deposit.domain.DepositRepository;
import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Deletes the deposits of a deleted account (after the deletion commits; retried through the outbox). */
@Component
class DepositAccountDeletionListener {

    private final DepositRepository deposits;

    DepositAccountDeletionListener(DepositRepository deposits) {
        this.deposits = deposits;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        deposits.deleteByOwner(event.userId());
    }
}
