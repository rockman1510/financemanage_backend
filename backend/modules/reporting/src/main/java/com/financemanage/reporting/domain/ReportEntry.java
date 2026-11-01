package com.financemanage.reporting.domain;

import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One ledger transaction in the read model. {@code deleted} keeps a tombstone so that an event arriving late (the
 * outbox does not guarantee the order) cannot bring a deleted transaction back. {@code excluded} is the user's
 * "exclude from reports" flag of an income or expense; the ledger also sets it on transfers and EXTERNAL movements,
 * which reports tell apart by their type instead (the cash flow shows EXTERNAL movements).
 */
public record ReportEntry(UUID transactionId, UUID ownerId, UUID walletId, String type, BigDecimal amount,
                          String currency, UUID categoryId, LocalDate date, boolean excluded, String source,
                          boolean deleted) {

    public static ReportEntry of(TransactionSnapshot t) {
        return new ReportEntry(t.transactionId(), t.ownerId(), t.walletId(), t.type(), t.amount(), t.currency(),
                t.categoryId(), t.occurredOn(), t.excludeFromReport() && isIncomeOrExpense(t.type()), t.source(),
                false);
    }

    private static boolean isIncomeOrExpense(String type) {
        return "INCOME".equals(type) || "EXPENSE".equals(type);
    }

    public ReportEntry asDeleted() {
        return new ReportEntry(transactionId, ownerId, walletId, type, amount, currency, categoryId, date, excluded,
                source, true);
    }
}
