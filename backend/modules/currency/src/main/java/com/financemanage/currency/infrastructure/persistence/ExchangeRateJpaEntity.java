package com.financemanage.currency.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exchange_rate", schema = "currency")
class ExchangeRateJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id")
    UUID ownerId;
    @Column(name = "base_currency", nullable = false, length = 3)
    String baseCurrency;
    @Column(name = "quote_currency", nullable = false, length = 3)
    String quoteCurrency;
    @Column(nullable = false)
    BigDecimal rate;
    @Column(name = "effective_date", nullable = false)
    LocalDate effectiveDate;
    @Column(nullable = false)
    String source;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
}
