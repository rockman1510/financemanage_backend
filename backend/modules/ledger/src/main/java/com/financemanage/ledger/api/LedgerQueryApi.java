package com.financemanage.ledger.api;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only facade used by other modules (budgets, bills, reports). */
public interface LedgerQueryApi {

    /** Every wallet of the owner, archived ones included. */
    List<WalletBalance> walletBalances(UUID ownerId);

    /** The owner's categories, parents before their sub-categories. */
    List<CategoryView> categories(UUID ownerId);

    /**
     * Expenses per currency between {@code from} and {@code to} (inclusive), counted like reports do (no transfers,
     * adjustments or transactions excluded from reports). An empty {@code categoryIds} means every category; a
     * parent category includes its sub-categories; {@code walletId} may be null (every wallet).
     */
    List<Money> expenseTotals(UUID ownerId, Set<UUID> categoryIds, UUID walletId, LocalDate from, LocalDate to);
}
