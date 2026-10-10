package com.financemanage.loan.api;

import com.financemanage.shared.domain.CashFlowItem;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only facade used by other modules (e.g. reporting). */
public interface LoanQueryApi {

    List<LoanPosition> activePositions(UUID ownerId, LocalDate asOf);

    List<CashFlowItem> expectedCashFlows(UUID ownerId, LocalDate from, LocalDate to);
}
