package com.financemanage.loan.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.loan.domain.LoanRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Deletes the loans of a deleted account (after the deletion commits; retried through the outbox). */
@Component
class LoanAccountDeletionListener {

    private final LoanRepository loans;

    LoanAccountDeletionListener(LoanRepository loans) {
        this.loans = loans;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        loans.deleteByOwner(event.userId());
    }
}
