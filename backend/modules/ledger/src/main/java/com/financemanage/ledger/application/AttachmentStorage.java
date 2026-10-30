package com.financemanage.ledger.application;

import java.io.InputStream;

/**
 * Outbound port for the bytes of attachments. The default adapter writes to the local disk; an object storage
 * (S3, R2…) adapter can replace it without touching the use cases.
 */
public interface AttachmentStorage {

    void store(String key, InputStream content);

    InputStream open(String key);

    /** Deletes the file; a missing file is not an error. */
    void delete(String key);
}
