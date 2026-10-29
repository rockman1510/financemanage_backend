package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.model.Attachment;
import java.io.InputStream;

/** An attachment and its bytes; the caller closes the stream. */
public record AttachmentContent(Attachment attachment, InputStream content) {
}
