package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "money_transaction", schema = "ledger")
class TransactionJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(name = "wallet_id", nullable = false)
    UUID walletId;
    @Enumerated(EnumType.STRING)
    TransactionType type;
    @Column(nullable = false)
    BigDecimal amount;
    @Column(nullable = false, length = 3)
    String currency;
    @Column(name = "category_id")
    UUID categoryId;
    @Column(name = "occurred_on", nullable = false)
    LocalDate occurredOn;
    String note;
    @Column(name = "with_person")
    String withPerson;
    @Column(name = "exclude_from_report", nullable = false)
    boolean excludeFromReport;
    @Column(name = "transfer_id")
    UUID transferId;
    @Column(name = "counterpart_wallet_id")
    UUID counterpartWalletId;
    @Column(name = "exchange_rate")
    BigDecimal exchangeRate;
    @Column(name = "recurring_id")
    UUID recurringId;
    @Column(name = "idempotency_key")
    String idempotencyKey;
    String source;
    @Column(name = "source_id")
    UUID sourceId;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;
}
