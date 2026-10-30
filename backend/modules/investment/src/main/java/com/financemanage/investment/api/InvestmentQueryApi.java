package com.financemanage.investment.api;

import com.financemanage.shared.domain.Money;
import java.util.List;
import java.util.UUID;

/** Read-only facade used by other modules (reports: net worth). */
public interface InvestmentQueryApi {

    /** Each asset the owner still holds: market value (cost when no price is known) and cost. */
    List<Position> positions(UUID ownerId);

    record Position(UUID assetId, String name, String type, Money marketValue, Money costBasis) {
    }
}
