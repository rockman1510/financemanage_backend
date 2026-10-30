package com.financemanage.deposit.infrastructure.persistence;

import com.financemanage.deposit.domain.DepositRepository;
import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.deposit.domain.model.DepositStatus;
import com.financemanage.deposit.domain.model.DepositTransaction;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link Deposit} aggregate to JPA entities. */
@Repository
class DepositRepositoryAdapter implements DepositRepository {

    private final DepositJpaRepository jpa;

    DepositRepositoryAdapter(DepositJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Deposit save(Deposit deposit) {
        DepositJpaEntity entity;
        if (deposit.getVersion() == null) {
            entity = new DepositJpaEntity();
            entity.id = deposit.getId();
        } else {
            entity = jpa.findById(deposit.getId())
                    .orElseThrow(() -> new OptimisticLockingFailureException("Deposit was deleted"));
            if (!Objects.equals(entity.version, deposit.getVersion())) {
                throw new OptimisticLockingFailureException("Deposit " + deposit.getId() + " was modified");
            }
        }
        copy(deposit, entity);
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<Deposit> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public List<Deposit> findByOwner(UUID ownerId, DepositStatus status) {
        List<DepositJpaEntity> rows = status == null
                ? jpa.findByOwnerIdOrderByOpenDateDesc(ownerId)
                : jpa.findByOwnerIdAndStatusOrderByOpenDateDesc(ownerId, status);
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    public List<Deposit> findActiveMaturingOnOrBefore(LocalDate date) {
        return jpa.findByStatusAndMaturityDateLessThanEqual(DepositStatus.ACTIVE, date).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public List<Deposit> findActiveMaturingOn(LocalDate date) {
        return jpa.findByStatusAndMaturityDate(DepositStatus.ACTIVE, date).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Deposit deposit) {
        jpa.deleteById(deposit.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteByOwnerId(ownerId);
    }

    private void copy(Deposit d, DepositJpaEntity e) {
        e.ownerId = d.getOwnerId();
        e.institutionName = d.getInstitutionName();
        e.accountNumber = d.getAccountNumber();
        e.type = d.getType();
        e.principalAmount = d.getPrincipal().amount();
        e.currency = d.getPrincipal().currencyCode();
        e.annualRate = d.getAnnualRate().value();
        e.earlyWithdrawalRate = d.getEarlyWithdrawalRate().value();
        e.termMonths = d.getTermMonths();
        e.openDate = d.getOpenDate();
        e.maturityDate = d.getMaturityDate();
        e.interestPaymentMethod = d.getInterestPaymentMethod();
        e.rolloverPolicy = d.getRolloverPolicy();
        e.calculationMethod = d.getCalculationMethod();
        e.status = d.getStatus();
        e.note = d.getNote();
        e.settledOn = d.getSettledOn();
        e.settledAmount = d.getSettledAmount() == null ? null : d.getSettledAmount().amount();
        e.updatedAt = Instant.now();

        // transactions are append-only: insert the ones the entity does not know yet
        Set<UUID> known = e.transactions.stream().map(t -> t.id).collect(Collectors.toSet());
        for (DepositTransaction tx : d.getTransactions()) {
            if (!known.contains(tx.id())) {
                DepositTransactionJpaEntity row = new DepositTransactionJpaEntity();
                row.id = tx.id();
                row.deposit = e;
                row.type = tx.type();
                row.amount = tx.amount().amount();
                row.occurredOn = tx.occurredOn();
                e.transactions.add(row);
            }
        }
    }

    private Deposit toDomain(DepositJpaEntity e) {
        Currency currency = Currency.getInstance(e.currency);
        List<DepositTransaction> txs = e.transactions.stream()
                .map(t -> new DepositTransaction(t.id, t.type, new Money(t.amount, currency), t.occurredOn))
                .toList();
        return new Deposit(e.id, e.ownerId, e.institutionName, e.accountNumber, e.type,
                new Money(e.principalAmount, currency), Percentage.of(e.annualRate),
                Percentage.of(e.earlyWithdrawalRate), e.termMonths, e.openDate, e.maturityDate,
                e.interestPaymentMethod, e.rolloverPolicy, e.calculationMethod, e.status, e.note, e.settledOn,
                e.settledAmount == null ? null : new Money(e.settledAmount, currency), txs, e.version);
    }
}
