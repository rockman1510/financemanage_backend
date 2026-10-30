package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.RecurringTransactionRepository;
import com.financemanage.ledger.domain.WalletRepository;
import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.RecurringTransaction;
import com.financemanage.ledger.domain.model.RecurringTransaction.NewRecurringTransaction;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recurring transaction rules. Due occurrences are recorded when a rule is created or resumed and by the daily
 * job; each occurrence has an idempotency key, so recording is safe to repeat.
 */
@Service
@Transactional
public class RecurringTransactionService {

    /** Upper bound of occurrences recorded in one go (e.g. a daily rule started long ago). */
    static final int MAX_CATCH_UP = 400;

    private final RecurringTransactionRepository rules;
    private final WalletRepository wallets;
    private final CategoryRepository categories;
    private final TransactionService transactions;
    private final Clock clock;

    public RecurringTransactionService(RecurringTransactionRepository rules, WalletRepository wallets,
                                       CategoryRepository categories, TransactionService transactions, Clock clock) {
        this.rules = rules;
        this.wallets = wallets;
        this.categories = categories;
        this.transactions = transactions;
        this.clock = clock;
    }

    public RecurringTransaction create(UUID ownerId, RecurringInput input) {
        Wallet wallet = wallet(input.walletId(), ownerId);
        Category category = category(input.categoryId(), ownerId);
        RecurringTransaction rule = RecurringTransaction.create(new NewRecurringTransaction(ownerId,
                new Money(input.amount(), wallet.currency()), input.note(), input.withPerson(), input.frequency(),
                input.interval(), input.startDate(), input.endDate()), wallet, category);
        return recordDue(rules.save(rule), today());
    }

    @Transactional(readOnly = true)
    public List<RecurringTransaction> list(UUID ownerId) {
        return rules.findByOwner(ownerId);
    }

    @Transactional(readOnly = true)
    public RecurringTransaction get(UUID id, UUID ownerId) {
        return load(id, ownerId);
    }

    public RecurringTransaction update(UUID id, UUID ownerId, RecurringChanges changes) {
        RecurringTransaction rule = load(id, ownerId);
        Wallet wallet = wallet(changes.walletId() != null ? changes.walletId() : rule.getWalletId(), ownerId);
        Category category = category(changes.categoryId() != null ? changes.categoryId() : rule.getCategoryId(),
                ownerId);
        Money amount = changes.amount() != null ? new Money(changes.amount(), wallet.currency()) : rule.getAmount();
        rule.change(wallet, category, amount, changes.note() != null ? changes.note() : rule.getNote(),
                changes.withPerson() != null ? changes.withPerson() : rule.getWithPerson());
        if (changes.endDate() != null) {
            rule.changeEndDate(changes.endDate());
        }
        if (Boolean.FALSE.equals(changes.active())) {
            rule.pause();
        } else if (Boolean.TRUE.equals(changes.active())) {
            rule.resume(today());
        }
        return recordDue(rules.save(rule), today());
    }

    public void delete(UUID id, UUID ownerId) {
        rules.delete(load(id, ownerId));
    }

    @Transactional(readOnly = true)
    public List<UUID> dueRuleIds(LocalDate asOf) {
        return rules.findDueIds(asOf);
    }

    /** Records the due occurrences of one rule; the job calls it per rule so one failure does not stop the others. */
    public void recordDue(UUID ruleId, LocalDate asOf) {
        rules.findById(ruleId).ifPresent(rule -> recordDue(rule, asOf));
    }

    private RecurringTransaction recordDue(RecurringTransaction rule, LocalDate asOf) {
        if (!rule.isDue(asOf)) {
            return rule;
        }
        int recorded = 0;
        while (rule.isDue(asOf) && recorded < MAX_CATCH_UP) {
            Optional<Wallet> wallet = wallets.findByIdAndOwner(rule.getWalletId(), rule.getOwnerId());
            boolean categoryExists = categories.findByIdAndOwner(rule.getCategoryId(), rule.getOwnerId()).isPresent();
            if (wallet.isEmpty() || wallet.get().isArchived() || !categoryExists) {
                rule.pause(); // the wallet was archived (or something was deleted): stop until the user fixes it
                break;
            }
            transactions.recordOccurrence(rule, rule.getNextOccurrence());
            rule.markGenerated();
            recorded++;
        }
        return rules.save(rule);
    }

    private RecurringTransaction load(UUID id, UUID ownerId) {
        return rules.findByIdAndOwner(id, ownerId)
                .orElseThrow(() -> new NotFoundException("RecurringTransaction", id));
    }

    private Wallet wallet(UUID id, UUID ownerId) {
        return wallets.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Wallet", id));
    }

    private Category category(UUID id, UUID ownerId) {
        return categories.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Category", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
