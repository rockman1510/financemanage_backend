package com.financemanage.deposit.infrastructure.persistence;

import com.financemanage.deposit.domain.model.DepositTransactionType;
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
@Table(name = "deposit_transaction", schema = "deposit")
class DepositTransactionJpaEntity {

    @Id
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deposit_id")
    DepositJpaEntity deposit;
    @Enumerated(EnumType.STRING)
    DepositTransactionType type;
    @Column(nullable = false)
    BigDecimal amount;
    @Column(name = "occurred_on", nullable = false)
    LocalDate occurredOn;
    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();
}
