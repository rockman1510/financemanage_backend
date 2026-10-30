package com.financemanage.investment.domain.model;

import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A buy, sell or income of an asset. {@code cash} is what left (buy: quantity × price + fee) or entered (sell:
 * quantity × price − fee; income: the amount) the wallet; {@code realized} is the gain of a sell or an income.
 */
public class Trade {

    private final UUID id;
    private final TradeType type;
    private final BigDecimal quantity;
    private final BigDecimal price;
    private final Money fee;
    private final Money cash;
    private final Money realized;
    private final LocalDate date;
    private final UUID walletId;
    private CashStatus cashStatus;
    private UUID transactionId;
    private final Instant createdAt;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Trade(UUID id, TradeType type, BigDecimal quantity, BigDecimal price, Money fee, Money cash,
                 Money realized, LocalDate date, UUID walletId, CashStatus cashStatus, UUID transactionId,
                 Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.quantity = quantity;
        this.price = price;
        this.fee = Objects.requireNonNull(fee);
        this.cash = Objects.requireNonNull(cash);
        this.realized = realized;
        this.date = Objects.requireNonNull(date);
        this.walletId = walletId;
        this.cashStatus = Objects.requireNonNull(cashStatus);
        this.transactionId = transactionId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** The ledger recorded (or refused) the cash; only a PENDING trade changes. */
    public void cashAnswered(boolean recorded, UUID ledgerTransactionId) {
        if (cashStatus == CashStatus.PENDING) {
            cashStatus = recorded ? CashStatus.RECORDED : CashStatus.FAILED;
            transactionId = ledgerTransactionId;
        }
    }

    public UUID getId() { return id; }
    public TradeType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public Money getFee() { return fee; }
    public Money getCash() { return cash; }
    public Money getRealized() { return realized; }
    public LocalDate getDate() { return date; }
    public UUID getWalletId() { return walletId; }
    public CashStatus getCashStatus() { return cashStatus; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }
}
