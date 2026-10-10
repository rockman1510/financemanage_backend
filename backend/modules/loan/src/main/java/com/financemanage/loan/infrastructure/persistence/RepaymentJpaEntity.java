package com.financemanage.loan.infrastructure.persistence;

import com.financemanage.loan.domain.model.RepaymentType;
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
@Table(name = "loan_repayment", schema = "loan")
class RepaymentJpaEntity {

    @Id
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id")
    LoanJpaEntity loan;
    @Enumerated(EnumType.STRING)
    RepaymentType type;
    @Column(name = "paid_on", nullable = false)
    LocalDate paidOn;
    @Column(nullable = false)
    BigDecimal amount;
    @Column(name = "principal_portion", nullable = false)
    BigDecimal principalPortion;
    @Column(name = "interest_portion", nullable = false)
    BigDecimal interestPortion;
    @Column(name = "fee_portion", nullable = false)
    BigDecimal feePortion;
    @Column(name = "idempotency_key")
    String idempotencyKey;
    @Column(name = "created_at", nullable = false)
    Instant createdAt = Instant.now();
}
