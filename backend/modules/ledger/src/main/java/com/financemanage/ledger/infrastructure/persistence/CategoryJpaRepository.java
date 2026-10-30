package com.financemanage.ledger.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface CategoryJpaRepository extends JpaRepository<CategoryJpaEntity, UUID> {

    Optional<CategoryJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<CategoryJpaEntity> findByOwnerIdOrderByCreatedAtAscNameAsc(UUID ownerId);

    boolean existsByParentId(UUID parentId);

    @Modifying
    @Query("delete from CategoryJpaEntity c where c.ownerId = :ownerId and c.parentId is not null")
    void deleteChildrenByOwnerId(UUID ownerId);

    @Modifying
    @Query("delete from CategoryJpaEntity c where c.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
