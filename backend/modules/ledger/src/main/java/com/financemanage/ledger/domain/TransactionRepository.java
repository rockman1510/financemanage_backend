package com.financemanage.ledger.domain;

import com.financemanage.ledger.domain.model.PageResult;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.TransactionFilter;
import com.financemanage.ledger.domain.model.TransactionTotals;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface TransactionRepository {

    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdAndOwner(UUID id, UUID ownerId);

    Optional<Transaction> findByIdempotencyKey(UUID ownerId, String idempotencyKey);

    /** Both legs of a transfer, the outgoing one first. */
    List<Transaction> findByTransfer(UUID ownerId, UUID transferId);

    /** Newest first (by date, then by creation time). */
    PageResult<Transaction> search(TransactionFilter filter, int page, int size);

    /** Income and expense per currency of the matching transactions. */
    List<TransactionTotals> totals(TransactionFilter filter);

    boolean existsByWallet(UUID walletId);

    boolean existsByCategory(UUID categoryId);

    void delete(Transaction transaction);

    void deleteByOwner(UUID ownerId);
}
