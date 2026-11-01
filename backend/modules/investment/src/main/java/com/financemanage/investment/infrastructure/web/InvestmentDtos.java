package com.financemanage.investment.infrastructure.web;

import com.financemanage.investment.application.InvestmentService.Portfolio;
import com.financemanage.investment.domain.model.Asset;
import com.financemanage.investment.domain.model.AssetType;
import com.financemanage.investment.domain.model.CashStatus;
import com.financemanage.investment.domain.model.Trade;
import com.financemanage.investment.domain.model.TradeType;
import com.financemanage.investment.domain.model.Valuation;
import com.financemanage.shared.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response contracts of the investment REST API (v1). */
final class InvestmentDtos {

    private InvestmentDtos() {
    }

    record CreateAssetRequest(@NotBlank @Size(max = 60) String name, @Size(max = 20) String symbol,
                              @NotNull AssetType type, @Pattern(regexp = "[A-Z]{3}") String currency,
                              @Size(max = 300) String note) {
    }

    /** Omitted fields stay unchanged; the type and currency cannot change. */
    record UpdateAssetRequest(@Size(max = 60) String name, @Size(max = 20) String symbol,
                              @Size(max = 300) String note) {
    }

    /** {@code walletId} is optional: without it the trade does not move money in the app. */
    record TradeRequest(@NotNull @Positive BigDecimal quantity, @NotNull @PositiveOrZero BigDecimal price,
                        @PositiveOrZero BigDecimal fee, UUID walletId, LocalDate date) {
    }

    /** {@code categoryId}: an income category, so reports count it as income (needs a wallet). */
    record IncomeRequest(@NotNull @Positive BigDecimal amount, UUID walletId, UUID categoryId, LocalDate date) {
    }

    record PriceRequest(@NotNull @PositiveOrZero BigDecimal price, LocalDate date) {
    }

    record TradeResponse(UUID id, TradeType type, BigDecimal quantity, BigDecimal price, BigDecimal fee,
                         BigDecimal cash, BigDecimal realized, LocalDate date, UUID walletId, CashStatus cashStatus,
                         UUID transactionId) {

        static TradeResponse from(Trade t) {
            return new TradeResponse(t.getId(), t.getType(), plain(t.getQuantity()), plain(t.getPrice()),
                    t.getFee().roundedAmount(), t.getCash().roundedAmount(), amount(t.getRealized()), t.getDate(),
                    t.getWalletId(), t.getCashStatus(), t.getTransactionId());
        }
    }

    /**
     * {@code marketValue} is quantity × last price (the cost while no price is known, {@code priced=false});
     * {@code unrealized} = market value − cost; {@code totalReturn} = unrealized + realized.
     */
    record AssetResponse(UUID id, String name, String symbol, AssetType type, String currency, String note,
                         BigDecimal quantity, BigDecimal averageCost, BigDecimal costBasis, BigDecimal lastPrice,
                         LocalDate priceDate, boolean priced, BigDecimal marketValue, BigDecimal unrealized,
                         BigDecimal unrealizedPercent, BigDecimal realized, BigDecimal totalReturn,
                         List<TradeResponse> trades) {

        static AssetResponse from(Asset a) {
            Valuation v = a.valuation();
            return new AssetResponse(a.getId(), a.getName(), a.getSymbol(), a.getType(),
                    a.getCurrency().getCurrencyCode(), a.getNote(), plain(a.getQuantity()), plain(v.averageCost()),
                    a.getCostBasis().roundedAmount(), plain(a.getLastPrice()), a.getPriceDate(), v.priced(),
                    v.marketValue().roundedAmount(), v.unrealized().roundedAmount(), v.unrealizedPercent(),
                    a.getRealized().roundedAmount(), v.totalReturn().roundedAmount(),
                    a.getTrades().stream().map(TradeResponse::from).toList());
        }
    }

    record AllocationResponse(AssetType type, BigDecimal value, BigDecimal percent) {
    }

    record PortfolioResponse(String currency, BigDecimal marketValue, BigDecimal costBasis, BigDecimal unrealized,
                             BigDecimal unrealizedPercent, BigDecimal realized, BigDecimal totalReturn,
                             List<AllocationResponse> allocation) {

        static PortfolioResponse from(Portfolio p) {
            return new PortfolioResponse(p.currency(), p.marketValue().roundedAmount(), p.costBasis().roundedAmount(),
                    p.unrealized().roundedAmount(), p.unrealizedPercent(), p.realized().roundedAmount(),
                    p.totalReturn().roundedAmount(), p.allocation().stream()
                    .map(x -> new AllocationResponse(x.type(), x.value().roundedAmount(), x.percent())).toList());
        }
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.roundedAmount();
    }

    private static BigDecimal plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().scale() < 0
                ? value.setScale(0) : value.stripTrailingZeros();
    }
}
