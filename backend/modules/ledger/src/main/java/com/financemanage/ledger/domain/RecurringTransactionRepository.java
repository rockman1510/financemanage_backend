package com.financemanage.ledger.domain;

import com.financemanage.ledger.domain.model.RecurringTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface RecurringTransactionRepository {

    RecurringTransaction save(RecurringTransaction rule);

    Optional<RecurringTransaction> findById(UUID id);

    Optional<RecurringTransaction> findByIdAndOwner(UUID id, UUID ownerId);

    List<RecurringTransaction> findByOwner(UUID ownerId);

    /** Ids of active rules whose next occurrence is on or before {@code date}. */
    List<UUID> findDueIds(LocalDate date);

    boolean existsByCategory(UUID categoryId);

    void deleteByWallet(UUID walletId);

    void delete(RecurringTransaction rule);
}
