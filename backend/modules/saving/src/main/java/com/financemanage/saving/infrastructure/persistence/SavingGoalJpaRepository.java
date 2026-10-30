package com.financemanage.saving.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SavingGoalJpaRepository extends JpaRepository<SavingGoalJpaEntity, UUID> {

    Optional<SavingGoalJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select m.goal from SavingMovementJpaEntity m where m.id = :movementId")
    Optional<SavingGoalJpaEntity> findByMovementId(UUID movementId);

    List<SavingGoalJpaEntity> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    List<SavingGoalJpaEntity> findByAutoFrequencyIsNotNull();

    List<SavingGoalJpaEntity> findByOwnerIdAndRoundUpWalletId(UUID ownerId, UUID walletId);
}
