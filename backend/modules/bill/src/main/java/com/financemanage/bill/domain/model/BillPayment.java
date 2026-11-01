package com.financemanage.bill.domain.model;

import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Payment (or skip) of one due date of a bill. */
public class BillPayment {

    private final UUID id;
    private final int occurrence;
    private final LocalDate dueDate;
    private final Money amount;
    private final UUID walletId;
    private final LocalDate paidOn;
    private PaymentStatus status;
    private UUID transactionId;
    private String failureReason;
    private final Instant createdAt;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public BillPayment(UUID id, int occurrence, LocalDate dueDate, Money amount, UUID walletId, LocalDate paidOn,
                       PaymentStatus status, UUID transactionId, String failureReason, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.occurrence = occurrence;
        this.dueDate = Objects.requireNonNull(dueDate);
        this.amount = amount;
        this.walletId = walletId;
        this.paidOn = paidOn;
        this.status = Objects.requireNonNull(status);
        this.transactionId = transactionId;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    void recorded(UUID ledgerTransactionId) {
        status = PaymentStatus.RECORDED;
        transactionId = ledgerTransactionId;
    }

    void failed(String reason) {
        status = PaymentStatus.FAILED;
        failureReason = reason;
    }

    public UUID getId() { return id; }
    public int getOccurrence() { return occurrence; }
    public LocalDate getDueDate() { return dueDate; }
    public Money getAmount() { return amount; }
    public UUID getWalletId() { return walletId; }
    public LocalDate getPaidOn() { return paidOn; }
    public PaymentStatus getStatus() { return status; }
    public UUID getTransactionId() { return transactionId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
