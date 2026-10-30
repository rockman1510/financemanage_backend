package com.financemanage.ledger.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface WalletJpaRepository extends JpaRepository<WalletJpaEntity, UUID> {

    Optional<WalletJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<WalletJpaEntity> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    List<WalletJpaEntity> findByOwnerIdAndArchivedFalseOrderByCreatedAtAsc(UUID ownerId);

    @Modifying
    @Query("delete from WalletJpaEntity w where w.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
