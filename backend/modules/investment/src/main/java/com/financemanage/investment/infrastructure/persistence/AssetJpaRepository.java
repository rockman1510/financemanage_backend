package com.financemanage.investment.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AssetJpaRepository extends JpaRepository<AssetJpaEntity, UUID> {

    Optional<AssetJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<AssetJpaEntity> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @Query("select a from AssetJpaEntity a where a.symbol is not null and a.quantity > 0")
    List<AssetJpaEntity> findHeldWithSymbol();
}
