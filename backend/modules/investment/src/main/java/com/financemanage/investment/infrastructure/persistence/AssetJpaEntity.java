package com.financemanage.investment.infrastructure.persistence;

import com.financemanage.investment.domain.model.AssetType;
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
@Table(name = "asset", schema = "investment")
class AssetJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(nullable = false)
    String name;
    String symbol;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AssetType type;
    @Column(nullable = false, length = 3)
    String currency;
    String note;
    @Column(nullable = false)
    BigDecimal quantity;
    @Column(name = "cost_basis", nullable = false)
    BigDecimal costBasis;
    @Column(nullable = false)
    BigDecimal realized;
    @Column(name = "last_price")
    BigDecimal lastPrice;
    @Column(name = "price_date")
    LocalDate priceDate;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    List<TradeJpaEntity> trades = new ArrayList<>();
}
