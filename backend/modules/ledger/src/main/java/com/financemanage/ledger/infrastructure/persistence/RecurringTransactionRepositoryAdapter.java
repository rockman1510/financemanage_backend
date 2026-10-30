package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.RecurringTransactionRepository;
import com.financemanage.ledger.domain.model.RecurringTransaction;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link RecurringTransaction} to its JPA entity. */
@Repository
class RecurringTransactionRepositoryAdapter implements RecurringTransactionRepository {

    private final RecurringTransactionJpaRepository jpa;

    RecurringTransactionRepositoryAdapter(RecurringTransactionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RecurringTransaction save(RecurringTransaction r) {
        RecurringTransactionJpaEntity e;
        if (r.getVersion() == null) {
            e = new RecurringTransactionJpaEntity();
            e.id = r.getId();
        } else {
            e = jpa.findById(r.getId())
                    .orElseThrow(() -> new OptimisticLockingFailureException("Recurring transaction was deleted"));
            if (!Objects.equals(e.version, r.getVersion())) {
                throw new OptimisticLockingFailureException("Recurring transaction " + r.getId() + " was modified");
            }
        }
        e.ownerId = r.getOwnerId();
        e.walletId = r.getWalletId();
        e.categoryId = r.getCategoryId();
        e.type = r.getType();
        e.amount = r.getAmount().amount();
        e.currency = r.getAmount().currencyCode();
        e.note = r.getNote();
        e.withPerson = r.getWithPerson();
        e.frequency = r.getFrequency();
        e.interval = r.getInterval();
        e.startDate = r.getStartDate();
        e.endDate = r.getEndDate();
        e.generatedCount = r.getGeneratedCount();
        e.nextOccurrence = r.getNextOccurrence();
        e.active = r.isActive();
        e.updatedAt = Instant.now();
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<RecurringTransaction> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<RecurringTransaction> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public List<RecurringTransaction> findByOwner(UUID ownerId) {
        return jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<UUID> findDueIds(LocalDate date) {
        return jpa.findDueIds(date);
    }

    @Override
    public boolean existsByCategory(UUID categoryId) {
        return jpa.existsByCategoryId(categoryId);
    }

    @Override
    public void deleteByWallet(UUID walletId) {
        jpa.deleteByWalletId(walletId);
    }

    @Override
    public void delete(RecurringTransaction rule) {
        jpa.deleteById(rule.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteByOwnerId(ownerId);
    }

    private RecurringTransaction toDomain(RecurringTransactionJpaEntity e) {
        return new RecurringTransaction(e.id, e.ownerId, e.walletId, e.categoryId, e.type,
                Money.of(e.amount, e.currency), e.note, e.withPerson, e.frequency, e.interval, e.startDate, e.endDate,
                e.generatedCount, e.nextOccurrence, e.active, e.version);
    }
}
