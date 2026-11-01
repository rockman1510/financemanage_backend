package com.financemanage.bill.infrastructure.persistence;

import com.financemanage.bill.domain.BillRepository;
import com.financemanage.bill.domain.model.Bill;
import com.financemanage.bill.domain.model.BillPayment;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
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

/** Maps the framework-free {@link Bill} aggregate (with its payments) to JPA entities. */
@Repository
class BillRepositoryAdapter implements BillRepository {

    private final BillJpaRepository jpa;

    BillRepositoryAdapter(BillJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Bill save(Bill bill) {
        BillJpaEntity e;
        if (bill.getVersion() == null) {
            e = new BillJpaEntity();
            e.id = bill.getId();
        } else {
            e = jpa.findById(bill.getId()).orElseThrow(() -> new OptimisticLockingFailureException("Bill was deleted"));
            if (!Objects.equals(e.version, bill.getVersion())) {
                throw new OptimisticLockingFailureException("Bill " + bill.getId() + " was modified");
            }
        }
        e.ownerId = bill.getOwnerId();
        e.name = bill.getName();
        e.amount = bill.getAmount().amount();
        e.currency = bill.getAmount().currencyCode();
        e.walletId = bill.getWalletId();
        e.categoryId = bill.getCategoryId();
        e.frequency = bill.getFrequency();
        e.interval = bill.getInterval();
        e.firstDueDate = bill.getFirstDueDate();
        e.endDate = bill.getEndDate();
        e.remindDaysBefore = bill.getRemindDaysBefore();
        e.note = bill.getNote();
        e.settledCount = bill.getSettledCount();
        e.nextDueDate = bill.getNextDueDate();
        e.active = bill.isActive();
        e.updatedAt = Instant.now();
        Map<UUID, BillPaymentJpaEntity> rows = e.payments.stream()
                .collect(Collectors.toMap(p -> p.id, Function.identity()));
        for (BillPayment payment : bill.getPayments()) {
            BillPaymentJpaEntity row = rows.get(payment.getId());
            if (row == null) {
                row = new BillPaymentJpaEntity();
                row.id = payment.getId();
                row.bill = e;
                e.payments.add(row);
            }
            row.occurrence = payment.getOccurrence();
            row.dueDate = payment.getDueDate();
            row.amount = payment.getAmount() == null ? null : payment.getAmount().amount();
            row.walletId = payment.getWalletId();
            row.paidOn = payment.getPaidOn();
            row.status = payment.getStatus();
            row.transactionId = payment.getTransactionId();
            row.failureReason = payment.getFailureReason();
            row.createdAt = payment.getCreatedAt();
        }
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Bill> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public Optional<Bill> findByPayment(UUID paymentId) {
        return jpa.findByPaymentId(paymentId).map(this::toDomain);
    }

    @Override
    public List<Bill> findByOwner(UUID ownerId) {
        return jpa.findByOwnerId(ownerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Bill> findActiveDueBetween(LocalDate from, LocalDate to) {
        return jpa.findByActiveTrueAndNextDueDateBetween(from, to).stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Bill bill) {
        jpa.deleteById(bill.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteAll(jpa.findByOwnerId(ownerId)); // payments go with their bill
    }

    private Bill toDomain(BillJpaEntity e) {
        Currency currency = Currency.getInstance(e.currency);
        List<BillPayment> payments = e.payments.stream()
                .map(p -> new BillPayment(p.id, p.occurrence, p.dueDate,
                        p.amount == null ? null : new Money(p.amount, currency), p.walletId, p.paidOn, p.status,
                        p.transactionId, p.failureReason, p.createdAt))
                .toList();
        return new Bill(e.id, e.ownerId, e.name, new Money(e.amount, currency), e.walletId, e.categoryId, e.frequency,
                e.interval, e.firstDueDate, e.endDate, e.remindDaysBefore, e.note, e.settledCount, e.nextDueDate,
                e.active, payments, e.version);
    }
}
