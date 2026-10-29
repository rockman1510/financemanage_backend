package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.AttachmentRepository;
import com.financemanage.ledger.domain.model.Attachment;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class AttachmentRepositoryAdapter implements AttachmentRepository {

    private final AttachmentJpaRepository jpa;
    private final EntityManager entityManager;

    AttachmentRepositoryAdapter(AttachmentJpaRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Attachment save(Attachment a) {
        AttachmentJpaEntity e = new AttachmentJpaEntity();
        e.id = a.id();
        e.ownerId = a.ownerId();
        e.transactionId = a.transactionId();
        e.fileName = a.fileName();
        e.contentType = a.contentType();
        e.size = a.size();
        e.storageKey = a.storageKey();
        e.createdAt = a.createdAt();
        entityManager.persist(e); // attachments are immutable: always a new row
        entityManager.flush();
        return toDomain(e);
    }

    @Override
    public Optional<Attachment> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public List<Attachment> findByTransaction(UUID transactionId) {
        return jpa.findByTransactionIdOrderByCreatedAtAsc(transactionId).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Attachment attachment) {
        jpa.deleteById(attachment.id());
    }

    private Attachment toDomain(AttachmentJpaEntity e) {
        return new Attachment(e.id, e.ownerId, e.transactionId, e.fileName, e.contentType, e.size, e.storageKey,
                e.createdAt);
    }
}
