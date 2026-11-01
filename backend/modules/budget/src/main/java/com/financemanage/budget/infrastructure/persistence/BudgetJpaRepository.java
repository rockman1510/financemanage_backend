package com.financemanage.budget.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BudgetJpaRepository extends JpaRepository<BudgetJpaEntity, UUID> {

    Optional<BudgetJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<BudgetJpaEntity> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);
}
