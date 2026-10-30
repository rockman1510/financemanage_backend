package com.financemanage.reporting.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "entry", schema = "reporting")
class ReportEntryJpaEntity {

    @Id
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(name = "wallet_id", nullable = false)
    UUID walletId;
    @Column(nullable = false)
    String type;
    @Column(nullable = false)
    BigDecimal amount;
    @Column(nullable = false, length = 3)
    String currency;
    @Column(name = "category_id")
    UUID categoryId;
    @Column(name = "entry_date", nullable = false)
    LocalDate date;
    @Column(nullable = false)
    boolean excluded;
    String source;
    @Column(nullable = false)
    boolean deleted;
}
