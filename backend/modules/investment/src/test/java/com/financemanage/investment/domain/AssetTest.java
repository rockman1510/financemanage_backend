package com.financemanage.investment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.investment.domain.model.Asset;
import com.financemanage.investment.domain.model.AssetType;
import com.financemanage.investment.domain.model.CashStatus;
import com.financemanage.investment.domain.model.Trade;
import com.financemanage.investment.domain.model.Valuation;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetTest {

    private static final LocalDate DAY = LocalDate.of(2025, 4, 1);
    private static final Instant NOW = Instant.parse("2025-04-01T00:00:00Z");
    private static final UUID WALLET = UUID.randomUUID();

    private final Asset fpt = Asset.create(UUID.randomUUID(), "FPT", "fpt", AssetType.STOCK,
            Currency.getInstance("VND"), null);

    @Test
    void buysAddToTheCostAtAveragePriceAndFees() {
        Trade first = fpt.buy(new BigDecimal("100"), new BigDecimal("100000"), new BigDecimal("15000"), WALLET, DAY,
                NOW);
        fpt.buy(new BigDecimal("100"), new BigDecimal("120000"), null, null, DAY.plusDays(1), NOW);

        assertThat(fpt.getSymbol()).isEqualTo("FPT");
        assertThat(first.getCash()).isEqualTo(Money.of(10_015_000, "VND"));
        assertThat(first.getCashStatus()).isEqualTo(CashStatus.PENDING);
        assertThat(fpt.getTrades().get(1).getCashStatus()).isEqualTo(CashStatus.NONE);
        assertThat(fpt.getQuantity()).isEqualByComparingTo("200");
        assertThat(fpt.getCostBasis()).isEqualTo(Money.of(22_015_000, "VND"));
        Valuation v = fpt.valuation();
        assertThat(v.averageCost()).isEqualByComparingTo("110075");
        assertThat(v.marketValue()).isEqualTo(Money.of(24_000_000, "VND")); // last price 120 000
        assertThat(v.unrealized()).isEqualTo(Money.of(1_985_000, "VND"));
        assertThat(v.unrealizedPercent()).isEqualByComparingTo("9.02");
    }

    @Test
    void sellsBookTheGainOverTheAverageCost() {
        fpt.buy(new BigDecimal("200"), new BigDecimal("100000"), null, null, DAY, NOW);

        Trade sell = fpt.sell(new BigDecimal("50"), new BigDecimal("130000"), new BigDecimal("10000"), WALLET,
                DAY.plusDays(2), NOW);

        assertThat(sell.getCash()).isEqualTo(Money.of(6_490_000, "VND"));
        assertThat(sell.getRealized()).isEqualTo(Money.of(1_490_000, "VND"));
        assertThat(fpt.getQuantity()).isEqualByComparingTo("150");
        assertThat(fpt.getCostBasis()).isEqualTo(Money.of(15_000_000, "VND"));
        assertThat(fpt.valuation().totalReturn()).isEqualTo(Money.of(1_490_000 + 4_500_000, "VND"));

        fpt.sell(new BigDecimal("150"), new BigDecimal("90000"), null, null, DAY.plusDays(3), NOW);
        assertThat(fpt.isHolding()).isFalse();
        assertThat(fpt.getCostBasis().isZero()).isTrue();
        assertThat(fpt.getRealized()).isEqualTo(Money.of(1_490_000 - 1_500_000, "VND"));
        assertThat(fpt.valuation().averageCost()).isNull();
    }

    @Test
    void cannotSellMoreThanHeldOrForLessThanTheFee() {
        fpt.buy(BigDecimal.TEN, new BigDecimal("1000"), null, null, DAY, NOW);

        assertThatThrownBy(() -> fpt.sell(new BigDecimal("11"), BigDecimal.ONE, null, null, DAY, NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INSUFFICIENT_QUANTITY");
        assertThatThrownBy(() -> fpt.sell(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, null, DAY, NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("FEE_TOO_HIGH");
        assertThat(fpt.getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void incomesAreRealizedGainsAndPricesOnlyMoveForward() {
        fpt.buy(BigDecimal.TEN, new BigDecimal("1000"), null, null, DAY, NOW);
        fpt.income(new BigDecimal("2000"), WALLET, DAY, NOW);
        fpt.updatePrice(new BigDecimal("1500"), DAY.plusDays(5));

        assertThat(fpt.getRealized()).isEqualTo(Money.of(2000, "VND"));
        assertThat(fpt.valuation().totalReturn()).isEqualTo(Money.of(7000, "VND"));
        assertThatThrownBy(() -> fpt.updatePrice(BigDecimal.ONE, DAY))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("STALE_PRICE");
        fpt.buy(BigDecimal.ONE, new BigDecimal("1000"), null, null, DAY.plusDays(1), NOW); // an older trade
        assertThat(fpt.getLastPrice()).isEqualByComparingTo("1500");
    }

    @Test
    void anUnpricedAssetIsWorthItsCostAndFractionalUnitsWork() {
        Asset gold = Asset.create(UUID.randomUUID(), "Vàng SJC", null, AssetType.GOLD, Currency.getInstance("VND"),
                null);
        Asset fresh = Asset.create(UUID.randomUUID(), "Nhà", null, AssetType.REAL_ESTATE,
                Currency.getInstance("VND"), null);
        gold.buy(new BigDecimal("0.5"), new BigDecimal("85000001"), null, null, DAY, NOW);

        assertThat(gold.getCostBasis()).isEqualTo(Money.of(42_500_001, "VND")); // rounded to whole đồng
        assertThat(fresh.valuation().priced()).isFalse();
        assertThat(fresh.valuation().marketValue().isZero()).isTrue();
        assertThat(fresh.valuation().unrealizedPercent()).isNull();
        assertThatThrownBy(() -> gold.buy(new BigDecimal("0.000000001"), BigDecimal.ONE, null, null, DAY, NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_QUANTITY");
    }

    @Test
    void cashAnswersOnlyChangePendingTrades() {
        Trade trade = fpt.buy(BigDecimal.ONE, BigDecimal.TEN, null, WALLET, DAY, NOW);

        trade.cashAnswered(false, null);
        trade.cashAnswered(true, UUID.randomUUID());

        assertThat(fpt.getTrades().getFirst().getCashStatus()).isEqualTo(CashStatus.FAILED);
        assertThat(fpt.getQuantity()).isEqualByComparingTo("1"); // the trade happened at the broker anyway
    }
}
