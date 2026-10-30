package com.financemanage.ledger.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface CategorySeedJpaRepository extends JpaRepository<CategorySeedJpaEntity, UUID> {

    @Modifying
    @Query("delete from CategorySeedJpaEntity s where s.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
