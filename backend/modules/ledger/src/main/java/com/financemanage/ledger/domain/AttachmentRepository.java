package com.financemanage.ledger.domain;

import com.financemanage.ledger.domain.model.Attachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface AttachmentRepository {

    Attachment save(Attachment attachment);

    Optional<Attachment> findByIdAndOwner(UUID id, UUID ownerId);

    List<Attachment> findByTransaction(UUID transactionId);

    void delete(Attachment attachment);

    List<Attachment> findByOwner(UUID ownerId);

    void deleteByOwner(UUID ownerId);
}
