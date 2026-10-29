package com.financemanage.ledger.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    Optional<CategoryJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<CategoryJpaEntity> findByOwnerIdOrderByCreatedAtAscNameAsc(UUID ownerId);

    boolean existsByParentId(UUID parentId);
}
