package com.financemanage.ledger.application;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.ledger.api.LedgerEvents.TransactionDeleted;
import com.financemanage.ledger.api.LedgerEvents.TransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerEvents.TransactionUpdated;
import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.TransactionRepository;
import com.financemanage.ledger.domain.WalletRepository;
import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.PageResult;
import com.financemanage.ledger.domain.model.RecurringTransaction;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.Transaction.NewTransaction;
import com.financemanage.ledger.domain.model.Transaction.TransferLegs;
import com.financemanage.ledger.domain.model.TransactionFilter;
import com.financemanage.ledger.domain.model.TransactionTotals;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction use cases. Every change updates the wallet balance in the same database transaction and publishes a
 * ledger event (through the outbox) with the transaction's snapshot.
 */
@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactions;
    private final WalletRepository wallets;
    private final CategoryRepository categories;
    private final AttachmentService attachments;
    private final CurrencyConversionApi conversion;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @SuppressWarnings("java:S107")
    public TransactionService(TransactionRepository transactions, WalletRepository wallets,
                              CategoryRepository categories, AttachmentService attachments,
                              CurrencyConversionApi conversion, ApplicationEventPublisher events, Clock clock) {
        this.transactions = transactions;
        this.wallets = wallets;
        this.categories = categories;
        this.attachments = attachments;
        this.conversion = conversion;
        this.events = events;
        this.clock = clock;
    }

    /** Records an income or expense. Calling again with the same idempotency key returns the first result. */
    public Transaction record(UUID ownerId, TransactionInput input, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactions.findByIdempotencyKey(ownerId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return recordNew(ownerId, input, null, idempotencyKey);
    }

    /** Records the occurrence of a recurring rule on {@code date}; does nothing when it was already recorded. */
    public void recordOccurrence(RecurringTransaction rule, LocalDate date) {
        String key = "recurring:" + rule.getId() + ":" + date;
        if (transactions.findByIdempotencyKey(rule.getOwnerId(), key).isEmpty()) {
            recordNew(rule.getOwnerId(), new TransactionInput(rule.getWalletId(), rule.getCategoryId(),
                    rule.getAmount().amount(), date, rule.getNote(), rule.getWithPerson(), false), rule.getId(), key);
        }
    }

    private Transaction recordNew(UUID ownerId, TransactionInput input, UUID recurringId, String idempotencyKey) {
        Wallet wallet = wallet(input.walletId(), ownerId);
        Category category = category(input.categoryId(), ownerId);
        Transaction transaction = Transaction.record(new NewTransaction(ownerId,
                new Money(input.amount(), wallet.currency()), dateOrToday(input.occurredOn()), input.note(),
                input.withPerson(), input.excludeFromReport(), recurringId, idempotencyKey), wallet, category);
        wallet.apply(transaction.signedAmount());
        wallets.save(wallet);
        Transaction saved = transactions.save(transaction);
        events.publishEvent(new TransactionRecorded(snapshot(saved)));
        return saved;
    }

    public Transaction update(UUID id, UUID ownerId, TransactionChanges changes) {
        Transaction transaction = load(id, ownerId);
        transaction.requireEditable();
        TransactionSnapshot before = snapshot(transaction);
        Wallet oldWallet = wallet(transaction.getWalletId(), ownerId);
        Wallet newWallet = changes.walletId() == null || changes.walletId().equals(oldWallet.getId())
                ? oldWallet : wallet(changes.walletId(), ownerId);
        Category category = category(
                changes.categoryId() != null ? changes.categoryId() : transaction.getCategoryId(), ownerId);
        Money amount = changes.amount() != null
                ? new Money(changes.amount(), newWallet.currency()) : transaction.getAmount();

        Money previous = transaction.signedAmount();
        transaction.update(newWallet, category, amount,
                changes.occurredOn() != null ? changes.occurredOn() : transaction.getOccurredOn(),
                changes.note() != null ? changes.note() : transaction.getNote(),
                changes.withPerson() != null ? changes.withPerson() : transaction.getWithPerson(),
                changes.excludeFromReport() != null ? changes.excludeFromReport()
                        : transaction.isExcludeFromReport());
        oldWallet.apply(previous.negated()); // balances change only once the new values are valid
        newWallet.apply(transaction.signedAmount());

        wallets.save(oldWallet);
        if (newWallet != oldWallet) {
            wallets.save(newWallet);
        }
        Transaction saved = transactions.save(transaction);
        events.publishEvent(new TransactionUpdated(before, snapshot(saved)));
        return saved;
    }

    /** Deletes the transaction; deleting one leg of a transfer deletes the whole transfer. */
    public void delete(UUID id, UUID ownerId) {
        Transaction transaction = load(id, ownerId);
        if (transaction.getTransferId() != null) {
            deleteTransfer(ownerId, transaction.getTransferId());
        } else {
            remove(transaction);
        }
    }

    public TransferLegs transfer(UUID ownerId, TransferInput input, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactions.findByIdempotencyKey(ownerId, idempotencyKey)
                    .filter(t -> t.getTransferId() != null);
            if (existing.isPresent()) {
                List<Transaction> legs = transactions.findByTransfer(ownerId, existing.get().getTransferId());
                return new TransferLegs(legs.get(0), legs.get(1));
            }
        }
        Wallet from = wallet(input.fromWalletId(), ownerId);
        Wallet to = wallet(input.toWalletId(), ownerId);
        LocalDate date = dateOrToday(input.occurredOn());
        Money amount = new Money(input.amount(), from.currency());
        Money received;
        if (input.receivedAmount() != null) {
            received = new Money(input.receivedAmount(), to.currency());
        } else if (from.currency().equals(to.currency())) {
            received = new Money(input.amount(), to.currency());
        } else {
            received = conversion.convert(ownerId, amount, to.currencyCode(), date);
        }
        TransferLegs legs = Transaction.transfer(ownerId, from, to, amount, received, date, input.note(),
                idempotencyKey);
        from.apply(legs.out().signedAmount());
        to.apply(legs.in().signedAmount());
        wallets.save(from);
        wallets.save(to);
        Transaction out = transactions.save(legs.out());
        Transaction in = transactions.save(legs.in());
        events.publishEvent(new TransactionRecorded(snapshot(out)));
        events.publishEvent(new TransactionRecorded(snapshot(in)));
        return new TransferLegs(out, in);
    }

    public void deleteTransfer(UUID ownerId, UUID transferId) {
        List<Transaction> legs = transactions.findByTransfer(ownerId, transferId);
        if (legs.isEmpty()) {
            throw new NotFoundException("Transfer", transferId);
        }
        legs.forEach(this::remove);
    }

    /** Records a balance adjustment so the wallet balance becomes {@code newBalance}. */
    public Transaction adjustBalance(UUID walletId, UUID ownerId, BigDecimal newBalance, LocalDate occurredOn,
                                     String note) {
        Wallet wallet = wallet(walletId, ownerId);
        Transaction adjustment = Transaction.adjustment(ownerId, wallet, new Money(newBalance, wallet.currency()),
                dateOrToday(occurredOn), note);
        wallet.apply(adjustment.signedAmount());
        wallets.save(wallet);
        Transaction saved = transactions.save(adjustment);
        events.publishEvent(new TransactionRecorded(snapshot(saved)));
        return saved;
    }

    @Transactional(readOnly = true)
    public TransactionView get(UUID id, UUID ownerId) {
        Transaction transaction = load(id, ownerId);
        return new TransactionView(transaction, attachments.listFor(transaction.getId()));
    }

    @Transactional(readOnly = true)
    public PageResult<Transaction> search(TransactionFilter filter, int page, int size) {
        return transactions.search(withSubCategories(filter), page, size);
    }

    @Transactional(readOnly = true)
    public List<TransactionTotals> totals(TransactionFilter filter) {
        return transactions.totals(withSubCategories(filter));
    }

    /** Filtering by a parent category also matches its sub-categories. */
    private TransactionFilter withSubCategories(TransactionFilter filter) {
        if (filter.categoryIds().isEmpty()) {
            return filter;
        }
        Set<UUID> ids = new HashSet<>(filter.categoryIds());
        categories.findByOwner(filter.ownerId(), null).stream()
                .filter(c -> c.getParentId() != null && filter.categoryIds().contains(c.getParentId()))
                .forEach(c -> ids.add(c.getId()));
        return filter.withCategoryIds(ids);
    }

    private void remove(Transaction transaction) {
        Wallet wallet = wallet(transaction.getWalletId(), transaction.getOwnerId());
        wallet.apply(transaction.signedAmount().negated());
        wallets.save(wallet);
        attachments.deleteAllOf(transaction.getId());
        transactions.delete(transaction);
        events.publishEvent(new TransactionDeleted(snapshot(transaction)));
    }

    static TransactionSnapshot snapshot(Transaction t) {
        return new TransactionSnapshot(t.getId(), t.getOwnerId(), t.getWalletId(), t.getType().name(),
                t.getAmount().amount(), t.signedAmount().amount(), t.getAmount().currencyCode(), t.getCategoryId(),
                t.getOccurredOn(), t.isExcludeFromReport(), t.getTransferId(), t.getRecurringId());
    }

    private Transaction load(UUID id, UUID ownerId) {
        return transactions.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Transaction", id));
    }

    private Wallet wallet(UUID id, UUID ownerId) {
        return wallets.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Wallet", id));
    }

    private Category category(UUID id, UUID ownerId) {
        return categories.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Category", id));
    }

    private LocalDate dateOrToday(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }
}
