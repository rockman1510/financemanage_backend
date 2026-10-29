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
     * balance (negative for expenses, outgoing transfers and downward adjustments).
     */
    public record TransactionSnapshot(UUID transactionId, UUID ownerId, UUID walletId, String type, BigDecimal amount,
                                      BigDecimal signedAmount, String currency, UUID categoryId, LocalDate occurredOn,
                                      boolean excludeFromReport, UUID transferId, UUID recurringId) {
    }

    public record TransactionRecorded(TransactionSnapshot transaction) {
    }

    public record TransactionUpdated(TransactionSnapshot before, TransactionSnapshot after) {
    }

    public record TransactionDeleted(TransactionSnapshot transaction) {
    }

    public record WalletChanged(UUID walletId, UUID ownerId) {
    }

    public record WalletDeleted(UUID walletId, UUID ownerId) {
    }
}
