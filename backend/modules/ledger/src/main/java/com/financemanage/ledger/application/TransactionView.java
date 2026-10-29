package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.model.Attachment;
import com.financemanage.ledger.domain.model.Transaction;
import java.util.List;

/** A transaction together with its attachments. */
public record TransactionView(Transaction transaction, List<Attachment> attachments) {
}
