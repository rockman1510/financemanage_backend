package com.financemanage.investment.domain.model;

import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;

/**
 * What a holding is worth: market value = quantity × last price (the cost when no price is known), unrealized gain =
 * market value − cost, {@code averageCost} per unit (null when nothing is held), returns in percent of the cost.
 */
public record Valuation(BigDecimal averageCost, Money marketValue, Money unrealized, BigDecimal unrealizedPercent,
                        Money totalReturn, boolean priced) {
}
