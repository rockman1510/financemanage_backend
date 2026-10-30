package com.financemanage.investment.infrastructure.price;

import com.financemanage.investment.domain.PriceProvider;
import com.financemanage.investment.domain.model.AssetType;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Default: no price feed, prices are entered by hand. A {@code @Primary} {@link PriceProvider} bean (a price feed)
 * replaces it.
 */
@Component
class ManualPriceProvider implements PriceProvider {

    @Override
    public Optional<Quote> latest(AssetType type, String symbol, String currency) {
        return Optional.empty();
    }
}
