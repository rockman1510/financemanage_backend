package com.financemanage.saving.domain;

import com.financemanage.saving.domain.model.SavingGoal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface SavingGoalRepository {

    SavingGoal save(SavingGoal goal);

    Optional<SavingGoal> findByIdAndOwner(UUID id, UUID ownerId);

    Optional<SavingGoal> findByMovement(UUID movementId);

    List<SavingGoal> findByOwner(UUID ownerId);

    /** Goals with an automatic contribution set up (due or not). */
    List<SavingGoal> findWithAutoContribution();

    List<SavingGoal> findRoundingUp(UUID ownerId, UUID walletId);

    void delete(SavingGoal goal);

    void deleteByOwner(UUID ownerId);
}
