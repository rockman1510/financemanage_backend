package com.financemanage.ledger.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain events published by the ledger module. Transaction events carry full snapshots so that other modules
 * (budgets, reports) can maintain their own read models without calling back. Primitive payloads keep them easy to
 * serialize (Kafka-ready).
 */
public final class LedgerEvents {

    private LedgerEvents() {
    }

    /**
     * State of a transaction. {@code amount} is always positive; {@code signedAmount} is its effect on the wallet
     * balance (negative for expenses, outgoing transfers and downward adjustments). {@code source}/{@code sourceId}
     * name the module record that requested it (null for the user's own entries).
     */
    public record TransactionSnapshot(UUID transactionId, UUID ownerId, UUID walletId, String type, BigDecimal amount,
                                      BigDecimal signedAmount, String currency, UUID categoryId, LocalDate occurredOn,
                                      boolean excludeFromReport, UUID transferId, UUID recurringId, String source,
                                      UUID sourceId) {
    }

    public record TransactionRecorded(TransactionSnapshot transaction) {
    }

    public record TransactionUpdated(TransactionSnapshot before, TransactionSnapshot after) {
    }

    public record TransactionDeleted(TransactionSnapshot transaction) {
    }

    public record WalletChanged(UUID walletId, UUID ownerId) {
    }

    /** Answer to {@code LedgerCommands.RecordExternalTransaction}: it was recorded as {@code transactionId}. */
    public record ExternalTransactionRecorded(UUID requestId, String source, UUID sourceId, UUID ownerId,
                                              UUID transactionId) {
    }

    /** Answer to {@code LedgerCommands.RecordExternalTransaction}: it could not be recorded (code + message). */
    public record ExternalTransactionRejected(UUID requestId, String source, UUID sourceId, UUID ownerId, String code,
                                              String message) {
    }

    public record WalletDeleted(UUID walletId, UUID ownerId) {
    }
}
