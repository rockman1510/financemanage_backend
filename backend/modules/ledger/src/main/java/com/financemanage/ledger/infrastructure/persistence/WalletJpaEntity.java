package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.model.WalletType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet", schema = "ledger")
class WalletJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(nullable = false)
    String name;
    @Enumerated(EnumType.STRING)
    WalletType type;
    @Column(nullable = false, length = 3)
    String currency;
    @Column(name = "initial_balance", nullable = false)
    BigDecimal initialBalance;
    @Column(nullable = false)
    BigDecimal balance;
    @Column(name = "excluded_from_total", nullable = false)
    boolean excludedFromTotal;
    @Column(nullable = false)
    boolean archived;
    String icon;
    @Column(name = "credit_limit")
    BigDecimal creditLimit;
    @Column(name = "statement_day")
    Integer statementDay;
    @Column(name = "payment_due_day")
    Integer paymentDueDay;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;
}
