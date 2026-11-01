package com.financemanage.bill.infrastructure.persistence;

import com.financemanage.bill.domain.model.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bill_payment", schema = "bill")
class BillPaymentJpaEntity {

    @Id
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id")
    BillJpaEntity bill;
    @Column(nullable = false)
    int occurrence;
    @Column(name = "due_date", nullable = false)
    LocalDate dueDate;
    BigDecimal amount;
    @Column(name = "wallet_id")
    UUID walletId;
    @Column(name = "paid_on")
    LocalDate paidOn;
    @Enumerated(EnumType.STRING)
    PaymentStatus status;
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "failure_reason")
    String failureReason;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
