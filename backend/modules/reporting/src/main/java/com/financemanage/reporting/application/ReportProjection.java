package com.financemanage.reporting.application;

import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.reporting.domain.ReportEntry;
import com.financemanage.reporting.domain.ReportEntryStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the reports' read model in step with the ledger's transaction events. The events may arrive out of order
 * (outbox, async): a "recorded" event never overwrites an entry an "updated" event already wrote, and a deleted
 * transaction stays a tombstone. An owner's existing transactions are loaded from the ledger once, on the first
 * report.
 */
@Service
@Transactional
public class ReportProjection {

    private final ReportEntryStore store;
    private final LedgerQueryApi ledger;

    public ReportProjection(ReportEntryStore store, LedgerQueryApi ledger) {
        this.store = store;
        this.ledger = ledger;
    }

    public void recorded(TransactionSnapshot t) {
        if (store.find(t.transactionId()).isEmpty()) {
            store.save(ReportEntry.of(t));
        }
    }

    public void updated(TransactionSnapshot after) {
        upsert(after);
    }

    public void deleted(TransactionSnapshot t) {
        store.save(ReportEntry.of(t).asDeleted());
    }

    /** Loads the owner's transactions from the ledger the first time a report is asked for. */
    public void ensureBuilt(UUID ownerId) {
        if (!store.isBuilt(ownerId)) {
            rebuild(ownerId);
        }
    }

    /** Writes the ledger's current state over the read model (tombstones stay). */
    public void rebuild(UUID ownerId) {
        ledger.transactions(ownerId).forEach(this::upsert);
        if (!store.isBuilt(ownerId)) {
            store.markBuilt(ownerId);
        }
    }

    public void deleteAllOf(UUID ownerId) {
        store.deleteByOwner(ownerId);
    }

    private void upsert(TransactionSnapshot t) {
        boolean tombstone = store.find(t.transactionId()).map(ReportEntry::deleted).orElse(false);
        if (!tombstone) {
            store.save(ReportEntry.of(t));
        }
    }
}
