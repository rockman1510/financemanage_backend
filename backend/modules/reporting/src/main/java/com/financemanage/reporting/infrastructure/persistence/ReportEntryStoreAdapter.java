package com.financemanage.reporting.infrastructure.persistence;

import com.financemanage.reporting.domain.ReportEntry;
import com.financemanage.reporting.domain.ReportEntryStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class ReportEntryStoreAdapter implements ReportEntryStore {

    private final ReportEntryJpaRepository entries;
    private final BuiltOwnerJpaRepository builtOwners;
    private final Clock clock;

    ReportEntryStoreAdapter(ReportEntryJpaRepository entries, BuiltOwnerJpaRepository builtOwners, Clock clock) {
        this.entries = entries;
        this.builtOwners = builtOwners;
        this.clock = clock;
    }

    @Override
    public Optional<ReportEntry> find(UUID transactionId) {
        return entries.findById(transactionId).map(ReportEntryStoreAdapter::toDomain);
    }

    @Override
    public void save(ReportEntry r) {
        ReportEntryJpaEntity e = entries.findById(r.transactionId()).orElseGet(ReportEntryJpaEntity::new);
        e.transactionId = r.transactionId();
        e.ownerId = r.ownerId();
        e.walletId = r.walletId();
        e.type = r.type();
        e.amount = r.amount();
        e.currency = r.currency();
        e.categoryId = r.categoryId();
        e.date = r.date();
        e.excluded = r.excluded();
        e.source = r.source();
        e.deleted = r.deleted();
        entries.save(e);
    }

    @Override
    public List<DailyTotal> dailyTotals(UUID ownerId, LocalDate from, LocalDate to) {
        return entries.dailyTotals(ownerId, from, to);
    }

    @Override
    public List<ReportEntry> entries(UUID ownerId, LocalDate from, LocalDate to) {
        return entries.findByOwnerIdAndDeletedFalseAndDateBetweenOrderByDateAsc(ownerId, from, to).stream()
                .map(ReportEntryStoreAdapter::toDomain).toList();
    }

    @Override
    public boolean isBuilt(UUID ownerId) {
        return builtOwners.existsById(ownerId);
    }

    @Override
    public void markBuilt(UUID ownerId) {
        BuiltOwnerJpaEntity e = new BuiltOwnerJpaEntity();
        e.ownerId = ownerId;
        e.builtAt = clock.instant();
        builtOwners.save(e);
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        entries.deleteByOwnerId(ownerId);
        builtOwners.deleteById(ownerId);
    }

    private static ReportEntry toDomain(ReportEntryJpaEntity e) {
        return new ReportEntry(e.transactionId, e.ownerId, e.walletId, e.type, e.amount, e.currency, e.categoryId,
                e.date, e.excluded, e.source, e.deleted);
    }
}
