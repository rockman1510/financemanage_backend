package com.financemanage.investment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.investment.application.InvestmentService.Portfolio;
import com.financemanage.investment.domain.AssetRepository;
import com.financemanage.investment.domain.PriceProvider;
import com.financemanage.investment.domain.model.Asset;
import com.financemanage.investment.domain.model.AssetType;
import com.financemanage.investment.domain.model.CashStatus;
import com.financemanage.investment.domain.model.Trade;
import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvestmentServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 4, 1);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();
    private static final UUID USD_WALLET = UUID.randomUUID();
    private static final UUID DIVIDENDS = UUID.randomUUID();

    private final List<Object> events = new ArrayList<>();
    private final InMemoryAssets repository = new InMemoryAssets();
    private final Map<String, PriceProvider.Quote> quotes = new LinkedHashMap<>();
    private final InvestmentService service = new InvestmentService(repository, new FakeLedger(),
            new FakeConversion(), (type, symbol, currency) -> Optional.ofNullable(quotes.get(symbol)), events::add,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void tradesWithAWalletAskTheLedgerToMoveTheCash() {
        Asset fpt = service.create(OWNER, "FPT", "FPT", AssetType.STOCK, null, null);

        Trade buy = service.buy(fpt.getId(), OWNER, new BigDecimal("100"), new BigDecimal("100000"),
                new BigDecimal("15000"), CASH, null);
        service.sell(fpt.getId(), OWNER, BigDecimal.TEN, new BigDecimal("110000"), null, null, null);
        service.income(fpt.getId(), OWNER, new BigDecimal("200000"), CASH, DIVIDENDS, null);

        assertThat(events).hasSize(2); // the sell has no wallet
        assertThat(events.getFirst()).isInstanceOfSatisfying(RecordExternalTransaction.class, c -> {
            assertThat(c.source()).isEqualTo("INVESTMENT");
            assertThat(c.requestId()).isEqualTo(buy.getId());
            assertThat(c.inflow()).isFalse();
            assertThat(c.categoryId()).isNull();
            assertThat(c.amount()).isEqualByComparingTo("10015000");
            assertThat(c.occurredOn()).isEqualTo(TODAY);
        });
        assertThat(events.get(1)).isInstanceOfSatisfying(RecordExternalTransaction.class, c -> {
            assertThat(c.inflow()).isTrue();
            assertThat(c.categoryId()).isEqualTo(DIVIDENDS);
        });
        service.cashRecorded(buy.getId(), UUID.randomUUID());
        assertThat(service.get(fpt.getId(), OWNER).getTrades().getFirst().getCashStatus())
                .isEqualTo(CashStatus.RECORDED);
    }

    @Test
    void walletsMustMatchAndCategoriesNeedAWallet() {
        Asset fpt = service.create(OWNER, "FPT", null, AssetType.STOCK, "VND", null);

        assertThatThrownBy(() -> service.buy(fpt.getId(), OWNER, BigDecimal.ONE, BigDecimal.ONE, null, USD_WALLET,
                null)).isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
        assertThatThrownBy(() -> service.income(fpt.getId(), OWNER, BigDecimal.ONE, null, DIVIDENDS, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("WALLET_REQUIRED");
        assertThat(service.get(fpt.getId(), OWNER).getTrades()).isEmpty();
    }

    @Test
    void thePortfolioAddsEveryAssetInOneCurrency() {
        Asset fpt = service.create(OWNER, "FPT", null, AssetType.STOCK, null, null);
        service.buy(fpt.getId(), OWNER, new BigDecimal("100"), new BigDecimal("100000"), null, null, null);
        service.updatePrice(fpt.getId(), OWNER, new BigDecimal("150000"), null);
        Asset btc = service.create(OWNER, "Bitcoin", "BTC", AssetType.CRYPTO, "USD", null);
        service.buy(btc.getId(), OWNER, new BigDecimal("0.1"), new BigDecimal("50000"), null, null, null);

        Portfolio p = service.portfolio(OWNER, null);

        assertThat(p.currency()).isEqualTo("VND");
        assertThat(p.costBasis()).isEqualTo(Money.of(10_000_000 + 125_000_000, "VND")); // 5000 USD × 25 000
        assertThat(p.marketValue()).isEqualTo(Money.of(15_000_000 + 125_000_000, "VND"));
        assertThat(p.unrealized()).isEqualTo(Money.of(5_000_000, "VND"));
        assertThat(p.allocation()).hasSize(2).anySatisfy(a -> {
            assertThat(a.type()).isEqualTo(AssetType.CRYPTO);
            assertThat(a.percent()).isEqualByComparingTo("89.29");
        });
        assertThat(service.positions(OWNER)).hasSize(2);
    }

    @Test
    void thePriceProviderUpdatesHeldAssetsWithASymbol() {
        Asset btc = service.create(OWNER, "Bitcoin", "btc", AssetType.CRYPTO, "USD", null);
        service.buy(btc.getId(), OWNER, BigDecimal.ONE, new BigDecimal("50000"), null, null, null);
        quotes.put("BTC", new PriceProvider.Quote(new BigDecimal("60000"), TODAY));

        assertThat(service.assetsToPrice()).hasSize(1);
        assertThat(service.refreshPrice(btc.getId(), OWNER)).isTrue();
        assertThat(service.get(btc.getId(), OWNER).valuation().unrealized()).isEqualTo(Money.of(10000, "USD"));
        quotes.clear();
        assertThat(service.refreshPrice(btc.getId(), OWNER)).isFalse();
    }

    private static class FakeLedger implements LedgerQueryApi {
        @Override
        public List<WalletBalance> walletBalances(UUID ownerId) {
            return List.of(new WalletBalance(CASH, "Tiền mặt", "CASH", Money.of(0, "VND"), false, false),
                    new WalletBalance(USD_WALLET, "USD", "CASH", Money.of(0, "USD"), false, false));
        }

        @Override
        public List<CategoryView> categories(UUID ownerId) {
            return List.of();
        }

        @Override
        public List<Money> expenseTotals(UUID ownerId, Set<UUID> categoryIds, UUID walletId, LocalDate from,
                                         LocalDate to) {
            return List.of();
        }
    }

    /** 1 USD = 25 000 VND. */
    private static class FakeConversion implements CurrencyConversionApi {
        @Override
        public Optional<BigDecimal> rate(UUID ownerId, String from, String to, LocalDate asOf) {
            return Optional.of(from.equals(to) ? BigDecimal.ONE : new BigDecimal("25000"));
        }

        @Override
        public Money convert(UUID ownerId, Money amount, String targetCurrency, LocalDate asOf) {
            return Money.of(amount.amount().multiply(rate(ownerId, amount.currencyCode(), targetCurrency, asOf)
                    .orElseThrow()), targetCurrency);
        }
    }

    private static class InMemoryAssets implements AssetRepository {
        final Map<UUID, Asset> store = new LinkedHashMap<>();

        @Override
        public Asset save(Asset asset) {
            store.put(asset.getId(), asset);
            return asset;
        }

        @Override
        public Optional<Asset> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(a -> a.getOwnerId().equals(ownerId));
        }

        @Override
        public void cashAnswered(UUID tradeId, boolean recorded, UUID transactionId) {
            store.values().stream().flatMap(a -> a.getTrades().stream()).filter(t -> t.getId().equals(tradeId))
                    .forEach(t -> t.cashAnswered(recorded, transactionId));
        }

        @Override
        public List<Asset> findByOwner(UUID ownerId) {
            return store.values().stream().filter(a -> a.getOwnerId().equals(ownerId)).toList();
        }

        @Override
        public List<Asset> findHeldWithSymbol() {
            return store.values().stream().filter(a -> a.getSymbol() != null && a.isHolding()).toList();
        }

        @Override
        public void delete(Asset asset) {
            store.remove(asset.getId());
        }

        @Override
        public void deleteByOwner(UUID ownerId) {
            store.values().removeIf(a -> a.getOwnerId().equals(ownerId));
        }
    }
}
