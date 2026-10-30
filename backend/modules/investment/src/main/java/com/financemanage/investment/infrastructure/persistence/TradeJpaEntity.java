package com.financemanage.investment.infrastructure.persistence;

import com.financemanage.investment.domain.model.CashStatus;
import com.financemanage.investment.domain.model.TradeType;
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
@Table(name = "trade", schema = "investment")
class TradeJpaEntity {

    @Id
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id")
    AssetJpaEntity asset;
    @Enumerated(EnumType.STRING)
    TradeType type;
    BigDecimal quantity;
    BigDecimal price;
    @Column(nullable = false)
    BigDecimal fee;
    @Column(nullable = false)
    BigDecimal cash;
    BigDecimal realized;
    @Column(name = "trade_date", nullable = false)
    LocalDate date;
    @Column(name = "wallet_id")
    UUID walletId;
    @Enumerated(EnumType.STRING)
    @Column(name = "cash_status")
    CashStatus cashStatus;
    @Column(name = "transaction_id")
    UUID transactionId;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
