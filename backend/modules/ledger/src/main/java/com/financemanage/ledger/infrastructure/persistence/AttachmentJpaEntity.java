package com.financemanage.ledger.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment", schema = "ledger")
class AttachmentJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(name = "transaction_id", nullable = false)
    UUID transactionId;
    @Column(name = "file_name", nullable = false)
    String fileName;
    @Column(name = "content_type", nullable = false)
    String contentType;
    @Column(name = "size_bytes", nullable = false)
    long size;
    @Column(name = "storage_key", nullable = false)
    String storageKey;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
