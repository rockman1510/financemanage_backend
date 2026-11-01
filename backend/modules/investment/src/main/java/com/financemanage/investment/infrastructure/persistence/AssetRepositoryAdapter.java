package com.financemanage.investment.infrastructure.persistence;

import com.financemanage.investment.domain.AssetRepository;
import com.financemanage.investment.domain.model.Asset;
import com.financemanage.investment.domain.model.CashStatus;
import com.financemanage.investment.domain.model.Trade;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link Asset} aggregate (with its trades) to JPA entities. */
@Repository
class AssetRepositoryAdapter implements AssetRepository {

    private final AssetJpaRepository jpa;
    private final TradeJpaRepository trades;

    AssetRepositoryAdapter(AssetJpaRepository jpa, TradeJpaRepository trades) {
        this.jpa = jpa;
        this.trades = trades;
    }

    @Override
    public Asset save(Asset a) {
        AssetJpaEntity e;
        if (a.getVersion() == null) {
            e = new AssetJpaEntity();
            e.id = a.getId();
        } else {
            e = jpa.findById(a.getId()).orElseThrow(() -> new OptimisticLockingFailureException("Asset was deleted"));
            if (!Objects.equals(e.version, a.getVersion())) {
                throw new OptimisticLockingFailureException("Asset " + a.getId() + " was modified");
            }
        }
        e.ownerId = a.getOwnerId();
        e.name = a.getName();
        e.symbol = a.getSymbol();
        e.type = a.getType();
        e.currency = a.getCurrency().getCurrencyCode();
        e.note = a.getNote();
        e.quantity = a.getQuantity();
        e.costBasis = a.getCostBasis().amount();
        e.realized = a.getRealized().amount();
        e.lastPrice = a.getLastPrice();
        e.priceDate = a.getPriceDate();
        e.updatedAt = Instant.now();
        Map<UUID, TradeJpaEntity> rows = e.trades.stream().collect(Collectors.toMap(t -> t.id, Function.identity()));
        for (Trade t : a.getTrades()) {
            TradeJpaEntity row = rows.get(t.getId());
            if (row == null) {
                row = new TradeJpaEntity();
                row.id = t.getId();
                row.asset = e;
                row.cashStatus = t.getCashStatus(); // later changed only by cashAnswered
                e.trades.add(row);
            }
            row.type = t.getType();
            row.quantity = t.getQuantity();
            row.price = t.getPrice();
            row.fee = t.getFee().amount();
            row.cash = t.getCash().amount();
            row.realized = t.getRealized() == null ? null : t.getRealized().amount();
            row.date = t.getDate();
            row.walletId = t.getWalletId();
            row.createdAt = t.getCreatedAt();
        }
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Asset> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public void cashAnswered(UUID tradeId, boolean recorded, UUID transactionId) {
        trades.answer(tradeId, recorded ? CashStatus.RECORDED : CashStatus.FAILED, transactionId);
    }

    @Override
    public List<Asset> findByOwner(UUID ownerId) {
        return jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Asset> findHeldWithSymbol() {
        return jpa.findHeldWithSymbol().stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Asset asset) {
        jpa.deleteById(asset.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteAll(jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId)); // trades go with their asset
    }

    private Asset toDomain(AssetJpaEntity e) {
        Currency currency = Currency.getInstance(e.currency);
        List<Trade> trades = e.trades.stream()
                .map(t -> new Trade(t.id, t.type, t.quantity, t.price, new Money(t.fee, currency),
                        new Money(t.cash, currency), t.realized == null ? null : new Money(t.realized, currency),
                        t.date, t.walletId, t.cashStatus, t.transactionId, t.createdAt))
                .toList();
        return new Asset(e.id, e.ownerId, e.name, e.symbol, e.type, currency, e.note, e.quantity,
                new Money(e.costBasis, currency), new Money(e.realized, currency), e.lastPrice, e.priceDate, trades,
                e.version);
    }
}
