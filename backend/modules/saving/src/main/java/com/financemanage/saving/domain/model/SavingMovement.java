package com.financemanage.saving.domain.model;

import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Money moved between a wallet and a goal. */
public class SavingMovement {

    private final UUID id;
    private final MovementType type;
    private final MovementSource source;
    private final Money amount;
    private final UUID walletId;
    private final LocalDate date;
    private MovementStatus status;
    private UUID transactionId;
    private String failureReason;
    private final Instant createdAt;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public SavingMovement(UUID id, MovementType type, MovementSource source, Money amount, UUID walletId,
                          LocalDate date, MovementStatus status, UUID transactionId, String failureReason,
                          Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.source = Objects.requireNonNull(source);
        this.amount = Objects.requireNonNull(amount);
        this.walletId = Objects.requireNonNull(walletId);
        this.date = Objects.requireNonNull(date);
        this.status = Objects.requireNonNull(status);
        this.transactionId = transactionId;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    void recorded(UUID ledgerTransactionId) {
        status = MovementStatus.RECORDED;
        transactionId = ledgerTransactionId;
    }

    void failed(String reason) {
        status = MovementStatus.FAILED;
        failureReason = reason;
    }

    /** Effect on the goal's saved amount while not failed. */
    Money effect() {
        return type == MovementType.DEPOSIT ? amount : amount.negated();
    }

    public UUID getId() { return id; }
    public MovementType getType() { return type; }
    public MovementSource getSource() { return source; }
    public Money getAmount() { return amount; }
    public UUID getWalletId() { return walletId; }
    public LocalDate getDate() { return date; }
    public MovementStatus getStatus() { return status; }
    public UUID getTransactionId() { return transactionId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
