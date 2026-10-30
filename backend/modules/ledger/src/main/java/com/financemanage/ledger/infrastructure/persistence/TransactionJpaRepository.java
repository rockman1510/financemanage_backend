package com.financemanage.ledger.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TransactionJpaRepository extends JpaRepository<TransactionJpaEntity, UUID>,
        JpaSpecificationExecutor<TransactionJpaEntity> {

    Optional<TransactionJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<TransactionJpaEntity> findByOwnerIdAndIdempotencyKey(UUID ownerId, String idempotencyKey);

    List<TransactionJpaEntity> findByOwnerIdAndTransferIdOrderByTypeDesc(UUID ownerId, UUID transferId);

    boolean existsByWalletId(UUID walletId);

    boolean existsByCategoryId(UUID categoryId);
}
