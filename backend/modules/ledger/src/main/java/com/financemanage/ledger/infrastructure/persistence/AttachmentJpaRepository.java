package com.financemanage.ledger.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttachmentJpaRepository extends JpaRepository<AttachmentJpaEntity, UUID> {

    Optional<AttachmentJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<AttachmentJpaEntity> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
