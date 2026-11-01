package com.financemanage.bill.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Bill aggregate (hoá đơn định kỳ): electricity, rent, internet… due on a schedule. Paying or skipping moves it to
 * the next due date. Due date {@code n} is computed from the first due date, so dates never drift. Pure Java so
 * business rules are easy to test.
 */
public class Bill {

    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_INTERVAL = 99;
    private static final int MAX_REMIND_DAYS = 30;

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private Money amount;
    private UUID walletId;
    private UUID categoryId;
    private final BillFrequency frequency;
    private final int interval;
    private final LocalDate firstDueDate;
    private LocalDate endDate;
    private int remindDaysBefore;
    private String note;
    /** Number of due dates already paid or skipped. */
    private int settledCount;
    private LocalDate nextDueDate;
    private boolean active;
    private final List<BillPayment> payments;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Bill(UUID id, UUID ownerId, String name, Money amount, UUID walletId, UUID categoryId,
                BillFrequency frequency, int interval, LocalDate firstDueDate, LocalDate endDate,
                int remindDaysBefore, String note, int settledCount, LocalDate nextDueDate, boolean active,
                List<BillPayment> payments, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = name;
        this.amount = Objects.requireNonNull(amount);
        this.walletId = Objects.requireNonNull(walletId);
        this.categoryId = Objects.requireNonNull(categoryId);
        this.frequency = Objects.requireNonNull(frequency);
        this.interval = interval;
        this.firstDueDate = Objects.requireNonNull(firstDueDate);
        this.endDate = endDate;
        this.remindDaysBefore = remindDaysBefore;
        this.note = note;
        this.settledCount = settledCount;
        this.nextDueDate = nextDueDate;
        this.active = active;
        this.payments = new ArrayList<>(payments == null ? List.of() : payments);
        this.version = version;
    }

    public static Bill create(NewBill spec) {
        if (spec.frequency() == null) {
            throw new DomainException("FREQUENCY_REQUIRED", "Frequency is required");
        }
        if (spec.interval() < 1 || spec.interval() > MAX_INTERVAL) {
            throw new DomainException("INVALID_INTERVAL", "Interval must be between 1 and " + MAX_INTERVAL);
        }
        if (spec.firstDueDate() == null) {
            throw new DomainException("DUE_DATE_REQUIRED", "First due date is required");
        }
        Bill bill = new Bill(UUID.randomUUID(), spec.ownerId(), validName(spec.name()), validAmount(spec.amount()),
                spec.walletId(), spec.categoryId(), spec.frequency(), spec.interval(), spec.firstDueDate(), null,
                validRemind(spec.remindDaysBefore()), text(spec.note()), 0, spec.firstDueDate(), true, List.of(),
                null);
        bill.changeEndDate(spec.endDate());
        return bill;
    }

    /** Partial update: {@code null} values stay unchanged. The schedule (frequency, interval, first date) is fixed. */
    public void update(String newName, Money newAmount, UUID newWalletId, UUID newCategoryId, LocalDate newEndDate,
                       Integer newRemindDaysBefore, String newNote) {
        if (newName != null) {
            name = validName(newName);
        }
        if (newAmount != null) {
            amount = validAmount(newAmount);
        }
        if (newWalletId != null) {
            walletId = newWalletId;
        }
        if (newCategoryId != null) {
            categoryId = newCategoryId;
        }
        if (newEndDate != null) {
            changeEndDate(newEndDate);
        }
        if (newRemindDaysBefore != null) {
            remindDaysBefore = validRemind(newRemindDaysBefore);
        }
        if (newNote != null) {
            note = text(newNote);
        }
    }

    /**
     * Pays the current due date with {@code paidAmount} from {@code fromWallet} and moves to the next one. The payment
     * is PENDING until the ledger records it.
     */
    public BillPayment pay(Money paidAmount, UUID fromWallet, LocalDate paidOn, Instant now) {
        requireDue();
        BillPayment payment = new BillPayment(UUID.randomUUID(), settledCount, nextDueDate, validAmount(paidAmount),
                fromWallet, paidOn, PaymentStatus.PENDING, null, null, now);
        payments.add(payment);
        advance();
        return payment;
    }

    /** Skips the current due date without paying (e.g. the bill was paid another way). */
    public BillPayment skip(Instant now) {
        requireDue();
        BillPayment skipped = new BillPayment(UUID.randomUUID(), settledCount, nextDueDate, null, null, null,
                PaymentStatus.SKIPPED, null, null, now);
        payments.add(skipped);
        advance();
        return skipped;
    }

    public void paymentRecorded(UUID paymentId, UUID transactionId) {
        payment(paymentId).recorded(transactionId);
    }

    /**
     * The ledger refused a payment: marks it FAILED and, when it was the latest settled due date, makes that date due
     * again (compensation of the saga).
     */
    public void paymentFailed(UUID paymentId, String reason) {
        BillPayment payment = payment(paymentId);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        payment.failed(reason);
        if (settledCount == payment.getOccurrence() + 1) {
            settledCount = payment.getOccurrence();
            nextDueDate = payment.getDueDate();
            active = true;
        }
    }

    public boolean isOverdue(LocalDate today) {
        return active && nextDueDate != null && nextDueDate.isBefore(today);
    }

    /** Days until the next due date (negative when overdue); null when the bill has ended. */
    public Long daysUntilDue(LocalDate today) {
        return nextDueDate == null ? null : ChronoUnit.DAYS.between(today, nextDueDate);
    }

    public void pause() {
        active = false;
    }

    public void resume() {
        if (nextDueDate != null) {
            active = true;
        }
    }

    private void changeEndDate(LocalDate newEndDate) {
        if (newEndDate != null && newEndDate.isBefore(firstDueDate)) {
            throw new DomainException("INVALID_END_DATE", "End date must not be before the first due date");
        }
        endDate = newEndDate;
        if (nextDueDate != null && endDate != null && nextDueDate.isAfter(endDate)) {
            finish();
        }
    }

    private void advance() {
        settledCount++;
        nextDueDate = frequency.add(firstDueDate, (long) settledCount * interval);
        if (endDate != null && nextDueDate.isAfter(endDate)) {
            finish();
        }
    }

    private void finish() {
        nextDueDate = null;
        active = false;
    }

    private void requireDue() {
        if (nextDueDate == null) {
            throw new DomainException("BILL_FINISHED", "The bill has no due date left");
        }
    }

    private BillPayment payment(UUID paymentId) {
        return payments.stream().filter(p -> p.getId().equals(paymentId)).findFirst()
                .orElseThrow(() -> new DomainException("PAYMENT_NOT_FOUND", "Unknown payment " + paymentId));
    }

    private static String validName(String value) {
        String name = text(value);
        if (name == null || name.length() > MAX_NAME_LENGTH) {
            throw new DomainException("BILL_NAME_REQUIRED", "Bill name is required and at most 60 characters");
        }
        return name;
    }

    private static Money validAmount(Money value) {
        if (value == null || !value.isPositive() || !value.hasMinorUnitPrecision()) {
            throw new DomainException("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        return value;
    }

    private static int validRemind(Integer days) {
        int value = days == null ? 3 : days;
        if (value < 0 || value > MAX_REMIND_DAYS) {
            throw new DomainException("INVALID_REMIND_DAYS", "Reminder must be 0 to 30 days before the due date");
        }
        return value;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public Money getAmount() { return amount; }
    public UUID getWalletId() { return walletId; }
    public UUID getCategoryId() { return categoryId; }
    public BillFrequency getFrequency() { return frequency; }
    public int getInterval() { return interval; }
    public LocalDate getFirstDueDate() { return firstDueDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getRemindDaysBefore() { return remindDaysBefore; }
    public String getNote() { return note; }
    public int getSettledCount() { return settledCount; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public boolean isActive() { return active; }
    public List<BillPayment> getPayments() { return Collections.unmodifiableList(payments); }
    public Long getVersion() { return version; }

    /** Input for a new bill; the amount is in the wallet's currency. */
    public record NewBill(UUID ownerId, String name, Money amount, UUID walletId, UUID categoryId,
                          BillFrequency frequency, int interval, LocalDate firstDueDate, LocalDate endDate,
                          Integer remindDaysBefore, String note) {
    }
}
