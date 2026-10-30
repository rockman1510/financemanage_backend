package com.financemanage.saving.infrastructure.persistence;

import com.financemanage.saving.domain.SavingGoalRepository;
import com.financemanage.saving.domain.model.SavingGoal;
import com.financemanage.saving.domain.model.SavingMovement;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link SavingGoal} aggregate (with its movements) to JPA entities. */
@Repository
class SavingGoalRepositoryAdapter implements SavingGoalRepository {

    private final SavingGoalJpaRepository jpa;

    SavingGoalRepositoryAdapter(SavingGoalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public SavingGoal save(SavingGoal g) {
        SavingGoalJpaEntity e;
        if (g.getVersion() == null) {
            e = new SavingGoalJpaEntity();
            e.id = g.getId();
        } else {
            e = jpa.findById(g.getId()).orElseThrow(() -> new OptimisticLockingFailureException("Goal was deleted"));
            if (!Objects.equals(e.version, g.getVersion())) {
                throw new OptimisticLockingFailureException("Goal " + g.getId() + " was modified");
            }
        }
        e.ownerId = g.getOwnerId();
        e.name = g.getName();
        e.targetAmount = g.getTarget().amount();
        e.currency = g.getTarget().currencyCode();
        e.deadline = g.getDeadline();
        e.icon = g.getIcon();
        e.savedAmount = g.getSaved().amount();
        e.milestone = g.getMilestone();
        e.autoAmount = g.getAutoAmount() == null ? null : g.getAutoAmount().amount();
        e.autoWalletId = g.getAutoWalletId();
        e.autoFrequency = g.getAutoFrequency();
        e.autoStart = g.getAutoStart();
        e.autoCount = g.getAutoCount();
        e.roundUpWalletId = g.getRoundUpWalletId();
        e.roundUpUnit = g.getRoundUpUnit();
        e.updatedAt = Instant.now();
        Map<UUID, SavingMovementJpaEntity> rows = e.movements.stream()
                .collect(Collectors.toMap(m -> m.id, Function.identity()));
        for (SavingMovement m : g.getMovements()) {
            SavingMovementJpaEntity row = rows.get(m.getId());
            if (row == null) {
                row = new SavingMovementJpaEntity();
                row.id = m.getId();
                row.goal = e;
                e.movements.add(row);
            }
            row.type = m.getType();
            row.source = m.getSource();
            row.amount = m.getAmount().amount();
            row.walletId = m.getWalletId();
            row.date = m.getDate();
            row.status = m.getStatus();
            row.transactionId = m.getTransactionId();
            row.failureReason = m.getFailureReason();
            row.createdAt = m.getCreatedAt();
        }
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<SavingGoal> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public Optional<SavingGoal> findByMovement(UUID movementId) {
        return jpa.findByMovementId(movementId).map(this::toDomain);
    }

    @Override
    public List<SavingGoal> findByOwner(UUID ownerId) {
        return jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<SavingGoal> findWithAutoContribution() {
        return jpa.findByAutoFrequencyIsNotNull().stream().map(this::toDomain).toList();
    }

    @Override
    public List<SavingGoal> findRoundingUp(UUID ownerId, UUID walletId) {
        return jpa.findByOwnerIdAndRoundUpWalletId(ownerId, walletId).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(SavingGoal goal) {
        jpa.deleteById(goal.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteAll(jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId)); // movements go with their goal
    }

    private SavingGoal toDomain(SavingGoalJpaEntity e) {
        Currency currency = Currency.getInstance(e.currency);
        List<SavingMovement> movements = e.movements.stream()
                .map(m -> new SavingMovement(m.id, m.type, m.source, new Money(m.amount, currency), m.walletId, m.date,
                        m.status, m.transactionId, m.failureReason, m.createdAt))
                .toList();
        return new SavingGoal(e.id, e.ownerId, e.name, new Money(e.targetAmount, currency), e.deadline, e.icon,
                new Money(e.savedAmount, currency), e.milestone,
                e.autoAmount == null ? null : new Money(e.autoAmount, currency), e.autoWalletId, e.autoFrequency,
                e.autoStart, e.autoCount, e.roundUpWalletId, e.roundUpUnit, movements, e.version);
    }
}
