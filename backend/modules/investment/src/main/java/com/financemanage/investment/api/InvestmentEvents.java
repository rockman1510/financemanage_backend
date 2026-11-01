package com.financemanage.investment.api;

import java.util.UUID;

/** Events published by the investment module. */
public final class InvestmentEvents {

    private InvestmentEvents() {
    }

    /** An asset, its holding or its price changed (reports refresh net worth). */
    public record PortfolioChanged(UUID ownerId) {
    }
}
