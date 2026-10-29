package com.financemanage.ledger.domain.model;

import java.time.Instant;
import java.util.UUID;

/** A file (e.g. a receipt photo) attached to a transaction; the bytes live in the attachment storage. */
public record Attachment(UUID id, UUID ownerId, UUID transactionId, String fileName, String contentType, long size,
                         String storageKey, Instant createdAt) {
}
