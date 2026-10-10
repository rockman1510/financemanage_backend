package com.financemanage.loan.infrastructure.persistence;

import com.financemanage.loan.domain.LoanRepository;
import com.financemanage.loan.domain.model.Installment;
import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import com.financemanage.loan.domain.model.RatePeriod;
import com.financemanage.loan.domain.model.Repayment;
import com.financemanage.loan.infrastructure.persistence.LoanJpaEntity.RatePeriodEmbeddable;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link Loan} aggregate to JPA entities. */
@Repository
class LoanRepositoryAdapter implements LoanRepository {

    private final LoanJpaRepository jpa;

    LoanRepositoryAdapter(LoanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Loan save(Loan loan) {
        LoanJpaEntity entity;
        if (loan.getVersion() == null) {
            entity = new LoanJpaEntity();
            entity.id = loan.getId();
        } else {
            entity = jpa.findById(loan.getId())
                    .orElseThrow(() -> new OptimisticLockingFailureException("Loan was deleted"));
            if (!Objects.equals(entity.version, loan.getVersion())) {
                throw new OptimisticLockingFailureException("Loan " + loan.getId() + " was modified");
            }
        }
        copy(loan, entity);
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<Loan> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public List<Loan> findByOwner(UUID ownerId, LoanDirection direction, LoanStatus status) {
        return jpa.search(ownerId, direction, status).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Loan> findActiveWithUnpaidInstallmentDueOn(LocalDate date) {
        return jpa.findActiveWithUnpaidInstallmentDueOn(date).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Loan loan) {
        jpa.deleteById(loan.getId());
    }

    private void copy(Loan loan, LoanJpaEntity e) {
        e.ownerId = loan.getOwnerId();
        e.counterpartyName = loan.getCounterpartyName();
        e.direction = loan.getDirection();
        e.type = loan.getType();
        e.principalAmount = loan.getPrincipal().amount();
        e.currency = loan.getPrincipal().currencyCode();
        e.termMonths = loan.getTermMonths();
        e.startDate = loan.getStartDate();
        e.repaymentMethod = loan.getRepaymentMethod();
        e.earlyRepaymentFeeRate = loan.getEarlyRepaymentFeeRate().value();
        e.status = loan.getStatus();
        e.note = loan.getNote();
        e.updatedAt = Instant.now();

        e.ratePeriods.clear();
        for (RatePeriod period : loan.getRatePeriods()) {
            RatePeriodEmbeddable row = new RatePeriodEmbeddable();
            row.effectiveFrom = period.effectiveFrom();
            row.annualRate = period.annualRate().value();
            e.ratePeriods.add(row);
        }

        // installments: update in place by id, insert new ones, remove regenerated ones
        Map<UUID, InstallmentJpaEntity> existing = e.installments.stream()
                .collect(Collectors.toMap(i -> i.id, Function.identity()));
        Set<UUID> current = loan.getInstallments().stream().map(Installment::getId).collect(Collectors.toSet());
        e.installments.removeIf(i -> !current.contains(i.id));
        for (Installment i : loan.getInstallments()) {
            InstallmentJpaEntity row = existing.get(i.getId());
            if (row == null) {
                row = new InstallmentJpaEntity();
                row.id = i.getId();
                row.loan = e;
                e.installments.add(row);
            }
            row.periodNo = i.getPeriodNo();
            row.dueDate = i.getDueDate();
            row.principalDue = i.getPrincipalDue().amount();
            row.interestDue = i.getInterestDue().amount();
            row.principalPaid = i.getPrincipalPaid().amount();
            row.interestPaid = i.getInterestPaid().amount();
            row.settled = i.isPaid();
        }

        // repayments are append-only
        Set<UUID> knownRepayments = e.repayments.stream().map(r -> r.id).collect(Collectors.toSet());
        for (Repayment r : loan.getRepayments()) {
            if (!knownRepayments.contains(r.id())) {
                RepaymentJpaEntity row = new RepaymentJpaEntity();
                row.id = r.id();
                row.loan = e;
                row.type = r.type();
                row.paidOn = r.paidOn();
                row.amount = r.amount().amount();
                row.principalPortion = r.principalPortion().amount();
                row.interestPortion = r.interestPortion().amount();
                row.feePortion = r.feePortion().amount();
                row.idempotencyKey = r.idempotencyKey();
                e.repayments.add(row);
            }
        }
    }

    private Loan toDomain(LoanJpaEntity e) {
        Currency currency = Currency.getInstance(e.currency);
        Function<java.math.BigDecimal, Money> money = amount -> new Money(amount, currency);
        List<RatePeriod> rates = e.ratePeriods.stream()
                .map(r -> new RatePeriod(r.effectiveFrom, Percentage.of(r.annualRate))).toList();
        List<Installment> installments = e.installments.stream()
                .map(i -> new Installment(i.id, i.periodNo, i.dueDate, money.apply(i.principalDue),
                        money.apply(i.interestDue), money.apply(i.principalPaid), money.apply(i.interestPaid)))
                .toList();
        List<Repayment> repayments = e.repayments.stream()
                .sorted((a, b) -> a.createdAt.compareTo(b.createdAt))
                .map(r -> new Repayment(r.id, r.type, r.paidOn, money.apply(r.amount),
                        money.apply(r.principalPortion), money.apply(r.interestPortion), money.apply(r.feePortion),
                        r.idempotencyKey))
                .toList();
        return new Loan(e.id, e.ownerId, e.counterpartyName, e.direction, e.type, money.apply(e.principalAmount),
                e.termMonths, e.startDate, e.repaymentMethod, rates, Percentage.of(e.earlyRepaymentFeeRate), e.status,
                e.note, installments, repayments, e.version);
    }
}
