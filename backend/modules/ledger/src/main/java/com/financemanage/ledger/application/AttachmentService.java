package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.AttachmentRepository;
import com.financemanage.ledger.domain.TransactionRepository;
import com.financemanage.ledger.domain.model.Attachment;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;

/**
 * Receipt photos and documents attached to transactions. Files are removed from the storage only after the database
 * change commits (and a file stored by a rolled back upload is removed again), so rows and files stay in step.
 */
@Service
@Transactional
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf");
    static final int MAX_PER_TRANSACTION = 5;
    private static final int MAX_FILE_NAME_LENGTH = 120;

    private final AttachmentRepository attachments;
    private final TransactionRepository transactions;
    private final AttachmentStorage storage;
    private final Clock clock;
    private final DataSize maxSize;

    public AttachmentService(AttachmentRepository attachments, TransactionRepository transactions,
                             AttachmentStorage storage, Clock clock,
                             @Value("${app.ledger.attachments.max-size:5MB}") DataSize maxSize) {
        this.attachments = attachments;
        this.transactions = transactions;
        this.storage = storage;
        this.clock = clock;
        this.maxSize = maxSize;
    }

    public Attachment upload(UUID ownerId, UUID transactionId, String fileName, String contentType, long size,
                             InputStream content) {
        Transaction transaction = transactions.findByIdAndOwner(transactionId, ownerId)
                .orElseThrow(() -> new NotFoundException("Transaction", transactionId));
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new DomainException("ATTACHMENT_TYPE_NOT_ALLOWED", "Allowed file types: " + ALLOWED_TYPES);
        }
        if (size <= 0 || size > maxSize.toBytes()) {
            throw new DomainException("ATTACHMENT_TOO_LARGE", "File must not be empty or larger than " + maxSize);
        }
        if (attachments.findByTransaction(transaction.getId()).size() >= MAX_PER_TRANSACTION) {
            throw new DomainException("TOO_MANY_ATTACHMENTS",
                    "A transaction has at most " + MAX_PER_TRANSACTION + " attachments");
        }
        UUID id = UUID.randomUUID();
        String key = ownerId + "/" + id;
        storage.store(key, content);
        afterCompletion(key, false);
        return attachments.save(new Attachment(id, ownerId, transaction.getId(), cleanFileName(fileName),
                contentType, size, key, clock.instant()));
    }

    @Transactional(readOnly = true)
    public AttachmentContent open(UUID id, UUID ownerId) {
        Attachment attachment = load(id, ownerId);
        return new AttachmentContent(attachment, storage.open(attachment.storageKey()));
    }

    @Transactional(readOnly = true)
    public List<Attachment> listFor(UUID transactionId) {
        return attachments.findByTransaction(transactionId);
    }

    public void delete(UUID id, UUID ownerId) {
        Attachment attachment = load(id, ownerId);
        attachments.delete(attachment);
        afterCompletion(attachment.storageKey(), true);
    }

    /** Removes every attachment of a transaction that is being deleted. */
    public void deleteAllOf(UUID transactionId) {
        for (Attachment attachment : attachments.findByTransaction(transactionId)) {
            attachments.delete(attachment);
            afterCompletion(attachment.storageKey(), true);
        }
    }

    /** Deletes the file after commit ({@code onCommit}) or after rollback (a stored file the database lost). */
    private void afterCompletion(String key, boolean onCommit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (onCommit) {
                deleteQuietly(key);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                boolean committed = status == STATUS_COMMITTED;
                if (committed == onCommit) {
                    deleteQuietly(key);
                }
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete attachment file {}: {}", key, e.getMessage());
        }
    }

    private Attachment load(UUID id, UUID ownerId) {
        return attachments.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Attachment", id));
    }

    /** Keeps the last path segment without control characters, e.g. "../../x\n.jpg" -> "x.jpg". */
    static String cleanFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            return "attachment";
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(name.length() - MAX_FILE_NAME_LENGTH) : name;
    }
}
