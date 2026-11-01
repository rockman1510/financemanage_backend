package com.financemanage.budget.infrastructure.persistence;

import com.financemanage.budget.domain.BudgetRepository;
import com.financemanage.budget.domain.model.Budget;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link Budget} aggregate to its JPA entity. */
@Repository
class BudgetRepositoryAdapter implements BudgetRepository {

    private final BudgetJpaRepository jpa;

    BudgetRepositoryAdapter(BudgetJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Budget save(Budget b) {
        BudgetJpaEntity e;
        if (b.getVersion() == null) {
            e = new BudgetJpaEntity();
            e.id = b.getId();
        } else {
            e = jpa.findById(b.getId()).orElseThrow(() -> new OptimisticLockingFailureException("Budget was deleted"));
            if (!Objects.equals(e.version, b.getVersion())) {
                throw new OptimisticLockingFailureException("Budget " + b.getId() + " was modified");
            }
        }
        e.ownerId = b.getOwnerId();
        e.name = b.getName();
        e.amount = b.getAmount().amount();
        e.currency = b.getAmount().currencyCode();
        e.categoryIds.clear();
        e.categoryIds.addAll(b.getCategoryIds());
        e.walletId = b.getWalletId();
        e.period = b.getPeriod();
        e.startDate = b.getStartDate();
        e.endDate = b.getEndDate();
        e.repeat = b.isRepeat();
        e.alertPeriodStart = b.getAlertPeriodStart();
        e.alertLevel = b.getAlertLevel();
        e.updatedAt = Instant.now();
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Budget> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public List<Budget> findByOwner(UUID ownerId) {
        return jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Budget budget) {
        jpa.deleteById(budget.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteAll(jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId)); // also removes the category rows
    }

    private Budget toDomain(BudgetJpaEntity e) {
        return new Budget(e.id, e.ownerId, e.name, Money.of(e.amount, e.currency), new HashSet<>(e.categoryIds),
                e.walletId, e.period, e.startDate, e.endDate, e.repeat, e.alertPeriodStart, e.alertLevel, e.version);
    }
}
