package com.financemanage.saving.infrastructure.persistence;

import com.financemanage.saving.domain.model.ContributionFrequency;
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
@Table(name = "goal", schema = "saving")
class SavingGoalJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(nullable = false)
    String name;
    @Column(name = "target_amount", nullable = false)
    BigDecimal targetAmount;
    @Column(nullable = false, length = 3)
    String currency;
    LocalDate deadline;
    String icon;
    @Column(name = "saved_amount", nullable = false)
    BigDecimal savedAmount;
    @Column(nullable = false)
    int milestone;
    @Column(name = "auto_amount")
    BigDecimal autoAmount;
    @Column(name = "auto_wallet_id")
    UUID autoWalletId;
    @Enumerated(EnumType.STRING)
    @Column(name = "auto_frequency")
    ContributionFrequency autoFrequency;
    @Column(name = "auto_start")
    LocalDate autoStart;
    @Column(name = "auto_count", nullable = false)
    int autoCount;
    @Column(name = "round_up_wallet_id")
    UUID roundUpWalletId;
    @Column(name = "round_up_unit")
    BigDecimal roundUpUnit;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    List<SavingMovementJpaEntity> movements = new ArrayList<>();
}
