package com.financemanage.ledger.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.RecurringTransactionRepository;
import com.financemanage.ledger.domain.TransactionRepository;
import com.financemanage.ledger.domain.WalletRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Deletes all ledger data of a deleted account – attachments (and their files), transactions, recurring rules,
 * categories and wallets, in that order because of the foreign keys. Runs after the deletion commits and is retried
 * through the outbox; deleting twice is harmless.
 */
@Component
class LedgerAccountDeletionListener {

    private final AttachmentService attachments;
    private final TransactionRepository transactions;
    private final RecurringTransactionRepository rules;
    private final CategoryRepository categories;
    private final WalletRepository wallets;

    LedgerAccountDeletionListener(AttachmentService attachments, TransactionRepository transactions,
                            RecurringTransactionRepository rules, CategoryRepository categories,
                            WalletRepository wallets) {
        this.attachments = attachments;
        this.transactions = transactions;
        this.rules = rules;
        this.categories = categories;
        this.wallets = wallets;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        attachments.deleteAllOfOwner(event.userId());
        transactions.deleteByOwner(event.userId());
        rules.deleteByOwner(event.userId());
        categories.deleteByOwner(event.userId());
        wallets.deleteByOwner(event.userId());
    }
}
