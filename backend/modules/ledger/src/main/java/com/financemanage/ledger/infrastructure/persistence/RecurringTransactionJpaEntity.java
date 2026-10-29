package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.model.Frequency;
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
@Table(name = "recurring_transaction", schema = "ledger")
class RecurringTransactionJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(name = "wallet_id", nullable = false)
    UUID walletId;
    @Column(name = "category_id", nullable = false)
    UUID categoryId;
    @Enumerated(EnumType.STRING)
    TransactionType type;
    @Column(nullable = false)
    BigDecimal amount;
    @Column(nullable = false, length = 3)
    String currency;
    String note;
    @Column(name = "with_person")
    String withPerson;
    @Enumerated(EnumType.STRING)
    Frequency frequency;
    @Column(name = "repeat_interval", nullable = false)
    int interval;
    @Column(name = "start_date", nullable = false)
    LocalDate startDate;
    @Column(name = "end_date")
    LocalDate endDate;
    @Column(name = "generated_count", nullable = false)
    int generatedCount;
    @Column(name = "next_occurrence")
    LocalDate nextOccurrence;
    @Column(nullable = false)
    boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;
}
