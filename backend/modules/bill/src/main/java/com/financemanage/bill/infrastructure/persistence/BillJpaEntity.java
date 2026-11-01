package com.financemanage.bill.infrastructure.persistence;

import com.financemanage.bill.domain.model.BillFrequency;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bill", schema = "bill")
class BillJpaEntity {

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
    @Column(name = "wallet_id", nullable = false)
    UUID walletId;
    @Column(name = "category_id", nullable = false)
    UUID categoryId;
    @Enumerated(EnumType.STRING)
    BillFrequency frequency;
    @Column(name = "repeat_interval", nullable = false)
    int interval;
    @Column(name = "first_due_date", nullable = false)
    LocalDate firstDueDate;
    @Column(name = "end_date")
    LocalDate endDate;
    @Column(name = "remind_days_before", nullable = false)
    int remindDaysBefore;
    String note;
    @Column(name = "settled_count", nullable = false)
    int settledCount;
    @Column(name = "next_due_date")
    LocalDate nextDueDate;
    @Column(nullable = false)
    boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurrence ASC, createdAt ASC")
    List<BillPaymentJpaEntity> payments = new ArrayList<>();
}
