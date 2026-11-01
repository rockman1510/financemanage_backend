package com.financemanage.ledger.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Commands other modules publish (as events, through the outbox) to have the ledger record money movements. The
 * ledger answers with {@code ExternalTransactionRecorded} or {@code ExternalTransactionRejected} carrying the same
 * {@code requestId}. This keeps every database transaction inside one module: a payment in another module and its
 * ledger entry are two steps, and a rejection lets the requester undo its own step (saga).
 */
public final class LedgerCommands {

    private LedgerCommands() {
    }

    /**
     * Records money leaving ({@code inflow = false}) or entering a wallet. With a {@code categoryId} it is an
     * ordinary expense or income (the category's type must match the direction); without one it is an
     * {@code EXTERNAL_OUT}/{@code EXTERNAL_IN} movement that reports never count as expense or income.
     *
     * @param requestId unique per request; repeating a request records it once
     * @param source    short name of the requesting module, e.g. "BILL"
     * @param sourceId  the requester's record, e.g. the bill payment
     * @param currency  must be the wallet's currency
     */
    public record RecordExternalTransaction(UUID requestId, String source, UUID sourceId, UUID ownerId,
                                            UUID walletId, boolean inflow, UUID categoryId, BigDecimal amount,
                                            String currency, LocalDate occurredOn, String note) {
    }
}
