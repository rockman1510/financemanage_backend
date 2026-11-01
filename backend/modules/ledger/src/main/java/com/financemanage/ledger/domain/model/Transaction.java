package com.financemanage.ledger.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A money movement in one wallet (giao dịch). {@code amount} is always positive in the wallet's currency; the
 * {@link TransactionType} gives its sign. A transfer is two transactions (out and in) sharing a {@code transferId}.
 * The wallet balance is updated by the application service in the same database transaction.
 */
public class Transaction {

    private static final int MAX_NOTE_LENGTH = 500;
    private static final int MAX_PERSON_LENGTH = 120;
    private static final int RATE_SCALE = 10;

    private final UUID id;
    private final UUID ownerId;
    private UUID walletId;
    private TransactionType type;
    private Money amount;
    private UUID categoryId;
    private LocalDate occurredOn;
    private String note;
    private String withPerson;
    private boolean excludeFromReport;
    private final UUID transferId;
    private final UUID counterpartWalletId;
    private final BigDecimal exchangeRate;
    private final UUID recurringId;
    private final String idempotencyKey;
    private final Origin origin;
    private final Instant createdAt;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Transaction(UUID id, UUID ownerId, UUID walletId, TransactionType type, Money amount, UUID categoryId,
                       LocalDate occurredOn, String note, String withPerson, boolean excludeFromReport,
                       UUID transferId, UUID counterpartWalletId, BigDecimal exchangeRate, UUID recurringId,
                       String idempotencyKey, Origin origin, Instant createdAt, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.walletId = Objects.requireNonNull(walletId);
        this.type = Objects.requireNonNull(type);
        this.amount = Objects.requireNonNull(amount);
        this.categoryId = categoryId;
        this.occurredOn = Objects.requireNonNull(occurredOn);
        this.note = note;
        this.withPerson = withPerson;
        this.excludeFromReport = excludeFromReport;
        this.transferId = transferId;
        this.counterpartWalletId = counterpartWalletId;
        this.exchangeRate = exchangeRate;
        this.recurringId = recurringId;
        this.idempotencyKey = idempotencyKey;
        this.origin = origin;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.version = version;
    }

    /** Income or expense; the category decides which. */
    public static Transaction record(NewTransaction spec, Wallet wallet, Category category) {
        requireSameOwner(spec.ownerId(), wallet, category);
        wallet.requireUsable();
        wallet.requireCurrency(spec.amount());
        if (spec.occurredOn() == null) {
            throw new DomainException("DATE_REQUIRED", "Transaction date is required");
        }
        return new Transaction(UUID.randomUUID(), spec.ownerId(), wallet.getId(),
                TransactionType.of(category.getType()), Texts.positiveAmount(spec.amount()), category.getId(),
                spec.occurredOn(), note(spec.note()), person(spec.withPerson()), spec.excludeFromReport(), null, null,
                null, spec.recurringId(), spec.idempotencyKey(), spec.origin(), Instant.now(), null);
    }

    /** Brings the wallet balance to {@code newBalance}; a zero difference is rejected. */
    public static Transaction adjustment(UUID ownerId, Wallet wallet, Money newBalance, LocalDate occurredOn,
                                         String note) {
        wallet.requireUsable();
        wallet.requireCurrency(newBalance);
        Money delta = Texts.precise(newBalance).minus(wallet.getBalance());
        if (delta.isZero()) {
            throw new DomainException("NOTHING_TO_ADJUST", "The wallet already has this balance");
        }
        TransactionType type = delta.isPositive() ? TransactionType.ADJUSTMENT_IN : TransactionType.ADJUSTMENT_OUT;
        return new Transaction(UUID.randomUUID(), ownerId, wallet.getId(), type, delta.isPositive() ? delta
                : delta.negated(), null, occurredOn, note(note), null, true, null, null, null, null, null, null,
                Instant.now(), null);
    }

    /**
     * Money moved between a wallet and another module's pot (a savings goal, an investment account…), requested by
     * that module. It changes the wallet balance but is never income or expense in reports.
     */
    public static Transaction external(UUID ownerId, Wallet wallet, boolean inflow, Money amount,
                                       LocalDate occurredOn, String note, Origin origin, String idempotencyKey) {
        if (!wallet.getOwnerId().equals(ownerId)) {
            throw new DomainException("FOREIGN_RESOURCE", "Wallet must belong to the same user");
        }
        wallet.requireUsable();
        wallet.requireCurrency(amount);
        if (occurredOn == null) {
            throw new DomainException("DATE_REQUIRED", "Transaction date is required");
        }
        return new Transaction(UUID.randomUUID(), ownerId, wallet.getId(),
                inflow ? TransactionType.EXTERNAL_IN : TransactionType.EXTERNAL_OUT, Texts.positiveAmount(amount),
                null, occurredOn, note(note), null, true, null, null, null, null, idempotencyKey,
                Objects.requireNonNull(origin), Instant.now(), null);
    }

    /**
     * Moves {@code amount} out of {@code from} and {@code receivedAmount} into {@code to}; the two amounts differ
     * only when the wallets hold different currencies. Transfers never count as income or expense in reports.
     */
    public static TransferLegs transfer(UUID ownerId, Wallet from, Wallet to, Money amount, Money receivedAmount,
                                        LocalDate occurredOn, String note, String idempotencyKey) {
        if (from.getId().equals(to.getId())) {
            throw new DomainException("SAME_WALLET", "Cannot transfer to the same wallet");
        }
        from.requireUsable();
        to.requireUsable();
        from.requireCurrency(amount);
        to.requireCurrency(receivedAmount);
        Texts.positiveAmount(amount);
        Texts.positiveAmount(receivedAmount);
        if (from.currency().equals(to.currency()) && amount.compareTo(receivedAmount) != 0) {
            throw new DomainException("TRANSFER_AMOUNT_MISMATCH",
                    "Wallets in the same currency must receive the amount sent");
        }
        if (occurredOn == null) {
            throw new DomainException("DATE_REQUIRED", "Transaction date is required");
        }
        UUID transferId = UUID.randomUUID();
        BigDecimal rate = receivedAmount.amount().divide(amount.amount(), MathContext.DECIMAL128)
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        String text = note(note);
        Transaction out = new Transaction(UUID.randomUUID(), ownerId, from.getId(), TransactionType.TRANSFER_OUT,
                amount, null, occurredOn, text, null, true, transferId, to.getId(), rate, null, idempotencyKey, null,
                now, null);
        Transaction in = new Transaction(UUID.randomUUID(), ownerId, to.getId(), TransactionType.TRANSFER_IN,
                receivedAmount, null, occurredOn, text, null, true, transferId, from.getId(), rate, null, null, null,
                now, null);
        return new TransferLegs(out, in);
    }

    /**
     * Replaces the editable fields of an income or expense. The caller passes the full new state (current values
     * for fields the user did not change).
     */
    public void update(Wallet wallet, Category category, Money newAmount, LocalDate newDate, String newNote,
                       String newPerson, boolean newExcludeFromReport) {
        requireEditable();
        requireSameOwner(ownerId, wallet, category);
        wallet.requireUsable();
        wallet.requireCurrency(newAmount);
        Money validAmount = Texts.positiveAmount(newAmount);
        String validNote = note(newNote);
        String validPerson = person(newPerson);
        walletId = wallet.getId();
        type = TransactionType.of(category.getType());
        categoryId = category.getId();
        amount = validAmount;
        occurredOn = Objects.requireNonNull(newDate);
        note = validNote;
        withPerson = validPerson;
        excludeFromReport = newExcludeFromReport;
    }

    public void requireEditable() {
        if (!type.isCategorized()) {
            throw new DomainException("TRANSACTION_NOT_EDITABLE",
                    "Transfers and balance adjustments cannot be edited; delete and record them again");
        }
    }

    /** Effect on the wallet balance. */
    public Money signedAmount() {
        return type.sign() > 0 ? amount : amount.negated();
    }

    private static void requireSameOwner(UUID ownerId, Wallet wallet, Category category) {
        if (!wallet.getOwnerId().equals(ownerId) || !category.getOwnerId().equals(ownerId)) {
            throw new DomainException("FOREIGN_RESOURCE", "Wallet and category must belong to the same user");
        }
    }

    private static String note(String value) {
        String text = Texts.optional(value);
        if (text != null && text.length() > MAX_NOTE_LENGTH) {
            throw new DomainException("NOTE_TOO_LONG", "Note is at most " + MAX_NOTE_LENGTH + " characters");
        }
        return text;
    }

    private static String person(String value) {
        String text = Texts.optional(value);
        if (text != null && text.length() > MAX_PERSON_LENGTH) {
            throw new DomainException("PERSON_TOO_LONG", "Person is at most " + MAX_PERSON_LENGTH + " characters");
        }
        return text;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getWalletId() { return walletId; }
    public TransactionType getType() { return type; }
    public Money getAmount() { return amount; }
    public UUID getCategoryId() { return categoryId; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getNote() { return note; }
    public String getWithPerson() { return withPerson; }
    public boolean isExcludeFromReport() { return excludeFromReport; }
    public UUID getTransferId() { return transferId; }
    public UUID getCounterpartWalletId() { return counterpartWalletId; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public UUID getRecurringId() { return recurringId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Origin getOrigin() { return origin; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getVersion() { return version; }

    /** Input for an income or expense; {@code origin} is set when another module requested it. */
    public record NewTransaction(UUID ownerId, Money amount, LocalDate occurredOn, String note, String withPerson,
                                 boolean excludeFromReport, UUID recurringId, String idempotencyKey, Origin origin) {
    }

    /** The module and record that asked for this transaction, e.g. ("BILL", payment id). */
    public record Origin(String source, UUID sourceId) {
        public Origin {
            Objects.requireNonNull(source);
            Objects.requireNonNull(sourceId);
        }
    }

    public record TransferLegs(Transaction out, Transaction in) {
    }
}
