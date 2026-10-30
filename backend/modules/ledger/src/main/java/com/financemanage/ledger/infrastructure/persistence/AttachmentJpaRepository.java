package com.financemanage.ledger.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface AttachmentJpaRepository extends JpaRepository<AttachmentJpaEntity, UUID> {

    Optional<AttachmentJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<AttachmentJpaEntity> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    List<AttachmentJpaEntity> findByOwnerId(UUID ownerId);

    @Modifying
    @Query("delete from AttachmentJpaEntity a where a.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
