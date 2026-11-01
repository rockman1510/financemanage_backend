package com.financemanage.budget.infrastructure.persistence;

import com.financemanage.budget.domain.model.BudgetPeriod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "budget", schema = "budget")
class BudgetJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(nullable = false)
    String name;
    @Column(nullable = false)
    BigDecimal amount;
    @Column(nullable = false, length = 3)
    String currency;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "budget_category", schema = "budget", joinColumns = @JoinColumn(name = "budget_id"))
    @Column(name = "category_id")
    Set<UUID> categoryIds = new HashSet<>();
    @Column(name = "wallet_id")
    UUID walletId;
    @Enumerated(EnumType.STRING)
    BudgetPeriod period;
    @Column(name = "start_date", nullable = false)
    LocalDate startDate;
    @Column(name = "end_date")
    LocalDate endDate;
    @Column(name = "repeat_period", nullable = false)
    boolean repeat;
    @Column(name = "alert_period_start")
    LocalDate alertPeriodStart;
    @Column(name = "alert_level", nullable = false)
    int alertLevel;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;
}
