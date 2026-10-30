package com.financemanage.budget.domain;

import com.financemanage.budget.domain.model.Budget;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface BudgetRepository {

    Budget save(Budget budget);

    Optional<Budget> findByIdAndOwner(UUID id, UUID ownerId);

    List<Budget> findByOwner(UUID ownerId);

    void delete(Budget budget);

    void deleteByOwner(UUID ownerId);
}
