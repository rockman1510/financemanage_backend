package com.financemanage.saving.infrastructure.persistence;

import com.financemanage.saving.domain.model.MovementSource;
import com.financemanage.saving.domain.model.MovementStatus;
import com.financemanage.saving.domain.model.MovementType;
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
@Table(name = "movement", schema = "saving")
class SavingMovementJpaEntity {

    @Id
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id")
    SavingGoalJpaEntity goal;
    @Enumerated(EnumType.STRING)
    MovementType type;
    @Enumerated(EnumType.STRING)
    MovementSource source;
    @Column(nullable = false)
    BigDecimal amount;
    @Column(name = "wallet_id", nullable = false)
    UUID walletId;
    @Column(name = "movement_date", nullable = false)
    LocalDate date;
    @Enumerated(EnumType.STRING)
    MovementStatus status;
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "failure_reason")
    String failureReason;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
