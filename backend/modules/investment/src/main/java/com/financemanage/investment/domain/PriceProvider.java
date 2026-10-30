package com.financemanage.investment.domain;

import com.financemanage.investment.domain.model.AssetType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Outbound port for market prices. The default adapter knows none (prices are entered by hand); an adapter for a
 * stock, gold or crypto price feed can replace it without touching the rest of the module.
 */
public interface PriceProvider {

    /** The latest price per unit of {@code symbol} in {@code currency}, if the provider knows it. */
    Optional<Quote> latest(AssetType type, String symbol, String currency);

    record Quote(BigDecimal price, LocalDate date) {
    }
}
