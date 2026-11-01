package com.financemanage.investment.application;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.investment.api.InvestmentEvents.PortfolioChanged;
import com.financemanage.investment.api.InvestmentQueryApi;
import com.financemanage.investment.domain.AssetRepository;
import com.financemanage.investment.domain.PriceProvider;
import com.financemanage.investment.domain.model.Asset;
import com.financemanage.investment.domain.model.AssetType;
import com.financemanage.investment.domain.model.CashStatus;
import com.financemanage.investment.domain.model.Trade;
import com.financemanage.investment.domain.model.TradeType;
import com.financemanage.investment.domain.model.Valuation;
import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Investment use cases. A trade changes the holding at once; when a wallet is given, the ledger is asked through a
 * {@link RecordExternalTransaction} command to record the cash (a buy as EXTERNAL_OUT, a sell as EXTERNAL_IN, an
 * income as income when a category is given). A refused cash entry only marks the trade: the trade itself happened at
 * the broker. Also serves the module's {@link InvestmentQueryApi}.
 */
@Service
@Transactional
public class InvestmentService implements InvestmentQueryApi {

    static final String SOURCE = "INVESTMENT";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AssetRepository assets;
    private final LedgerQueryApi ledger;
    private final CurrencyConversionApi conversion;
    private final PriceProvider prices;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InvestmentService(AssetRepository assets, LedgerQueryApi ledger, CurrencyConversionApi conversion,
                             PriceProvider prices, ApplicationEventPublisher events, Clock clock) {
        this.assets = assets;
        this.ledger = ledger;
        this.conversion = conversion;
        this.prices = prices;
        this.events = events;
        this.clock = clock;
    }

    public Asset create(UUID ownerId, String name, String symbol, AssetType type, String currency, String note) {
        return changed(assets.save(Asset.create(ownerId, name, symbol, type, Money.currency(currency == null ? "VND"
                : currency), note)));
    }

    @Transactional(readOnly = true)
    public List<Asset> list(UUID ownerId) {
        return assets.findByOwner(ownerId);
    }

    @Transactional(readOnly = true)
    public Asset get(UUID id, UUID ownerId) {
        return load(id, ownerId);
    }

    public Asset update(UUID id, UUID ownerId, String name, String symbol, String note) {
        Asset asset = load(id, ownerId);
        asset.update(name, symbol, note);
        return changed(assets.save(asset));
    }

    /** Stops tracking the asset; wallet transactions already recorded for its trades stay. */
    public void delete(UUID id, UUID ownerId) {
        Asset asset = load(id, ownerId);
        assets.delete(asset);
        changed(asset);
    }

    public Trade buy(UUID id, UUID ownerId, BigDecimal quantity, BigDecimal price, BigDecimal fee, UUID walletId,
                     LocalDate date) {
        Asset asset = load(id, ownerId);
        checkWallet(asset, walletId);
        Trade trade = asset.buy(quantity, price, fee, walletId, day(date), clock.instant());
        return recorded(asset, trade, null);
    }

    public Trade sell(UUID id, UUID ownerId, BigDecimal quantity, BigDecimal price, BigDecimal fee, UUID walletId,
                      LocalDate date) {
        Asset asset = load(id, ownerId);
        checkWallet(asset, walletId);
        Trade trade = asset.sell(quantity, price, fee, walletId, day(date), clock.instant());
        return recorded(asset, trade, null);
    }

    /** Dividend, coupon or rent; with a {@code categoryId} (an income category) reports count it as income. */
    public Trade income(UUID id, UUID ownerId, BigDecimal amount, UUID walletId, UUID categoryId, LocalDate date) {
        Asset asset = load(id, ownerId);
        checkWallet(asset, walletId);
        if (categoryId != null && walletId == null) {
            throw new DomainException("WALLET_REQUIRED", "A category needs a wallet to record the income in");
        }
        Trade trade = asset.income(amount, walletId, day(date), clock.instant());
        return recorded(asset, trade, categoryId);
    }

    public Asset updatePrice(UUID id, UUID ownerId, BigDecimal price, LocalDate date) {
        Asset asset = load(id, ownerId);
        asset.updatePrice(price, day(date));
        return changed(assets.save(asset));
    }

    public void cashRecorded(UUID tradeId, UUID transactionId) {
        assets.cashAnswered(tradeId, true, transactionId);
    }

    /** Only the trade is marked: it happened at the broker even if the wallet could not pay or receive it. */
    public void cashRejected(UUID tradeId) {
        assets.cashAnswered(tradeId, false, null);
    }

    /** Price job: assets still held whose symbol the provider knows. */
    @Transactional(readOnly = true)
    public List<Asset> assetsToPrice() {
        return assets.findHeldWithSymbol();
    }

    /** Asks the provider for a newer price; true when the asset's price changed. */
    public boolean refreshPrice(UUID id, UUID ownerId) {
        Asset asset = load(id, ownerId);
        return prices.latest(asset.getType(), asset.getSymbol(), asset.getCurrency().getCurrencyCode())
                .filter(q -> asset.getPriceDate() == null || !q.date().isBefore(asset.getPriceDate()))
                .map(q -> {
                    asset.updatePrice(q.price(), q.date());
                    changed(assets.save(asset));
                    return true;
                }).orElse(false);
    }

    /** Every asset converted to {@code currency}, with the split by asset type (allocation). */
    @Transactional(readOnly = true)
    public Portfolio portfolio(UUID ownerId, String currency) {
        String target = Money.currency(currency == null ? "VND" : currency).getCurrencyCode();
        LocalDate today = LocalDate.now(clock);
        Money zero = Money.zero(Money.currency(target));
        Money market = zero;
        Money cost = zero;
        Money realized = zero;
        Map<AssetType, Money> byType = new EnumMap<>(AssetType.class);
        for (Asset asset : assets.findByOwner(ownerId)) {
            Valuation v = asset.valuation();
            Money value = conversion.convert(ownerId, v.marketValue(), target, today);
            market = market.plus(value);
            cost = cost.plus(conversion.convert(ownerId, asset.getCostBasis(), target, today));
            realized = realized.plus(conversion.convert(ownerId, asset.getRealized(), target, today));
            if (asset.isHolding()) {
                byType.merge(asset.getType(), value, Money::plus);
            }
        }
        Money total = market;
        List<Allocation> allocation = byType.entrySet().stream()
                .map(e -> new Allocation(e.getKey(), e.getValue(), total.isPositive()
                        ? e.getValue().amount().multiply(HUNDRED).divide(total.amount(), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO))
                .toList();
        Money unrealized = market.minus(cost);
        BigDecimal percent = cost.isPositive()
                ? unrealized.amount().multiply(HUNDRED).divide(cost.amount(), 2, RoundingMode.HALF_UP) : null;
        return new Portfolio(target, market, cost, unrealized, percent, realized, unrealized.plus(realized),
                allocation);
    }

    public void deleteAllOf(UUID ownerId) {
        assets.deleteByOwner(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Position> positions(UUID ownerId) {
        return assets.findByOwner(ownerId).stream().filter(Asset::isHolding)
                .map(a -> new Position(a.getId(), a.getName(), a.getType().name(), a.valuation().marketValue(),
                        a.getCostBasis()))
                .toList();
    }

    public record Allocation(AssetType type, Money value, BigDecimal percent) {
    }

    /** Totals in one currency; {@code totalReturn} = unrealized + realized gains. */
    public record Portfolio(String currency, Money marketValue, Money costBasis, Money unrealized,
                            BigDecimal unrealizedPercent, Money realized, Money totalReturn,
                            List<Allocation> allocation) {
    }

    private Trade recorded(Asset asset, Trade trade, UUID categoryId) {
        changed(assets.save(asset));
        if (trade.getCashStatus() == CashStatus.PENDING) {
            boolean inflow = trade.getType() != TradeType.BUY;
            events.publishEvent(new RecordExternalTransaction(trade.getId(), SOURCE, trade.getId(), asset.getOwnerId(),
                    trade.getWalletId(), inflow, categoryId, trade.getCash().amount(), trade.getCash().currencyCode(),
                    trade.getDate(), noteOf(trade.getType()) + asset.getName()));
        }
        return trade;
    }

    private static String noteOf(TradeType type) {
        return switch (type) {
            case BUY -> "Mua: ";
            case SELL -> "Bán: ";
            case INCOME -> "Cổ tức/lãi: ";
        };
    }

    /** An optional wallet must belong to the owner, not be archived and hold the asset's currency. */
    private void checkWallet(Asset asset, UUID walletId) {
        if (walletId == null) {
            return;
        }
        WalletBalance wallet = ledger.walletBalances(asset.getOwnerId()).stream()
                .filter(w -> w.walletId().equals(walletId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Wallet", walletId));
        if (wallet.archived()) {
            throw new DomainException("WALLET_ARCHIVED", "Wallet " + wallet.name() + " is archived");
        }
        if (!wallet.balance().currency().equals(asset.getCurrency())) {
            throw new DomainException("CURRENCY_MISMATCH", "The asset is in " + asset.getCurrency());
        }
    }

    private Asset changed(Asset asset) {
        events.publishEvent(new PortfolioChanged(asset.getOwnerId()));
        return asset;
    }

    private Asset load(UUID id, UUID ownerId) {
        return assets.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Asset", id));
    }

    private LocalDate day(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }
}
