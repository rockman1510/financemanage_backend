package com.financemanage.ledger.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Rule that records the same income or expense on a schedule (giao dịch định kỳ), e.g. rent on the 5th of every
 * month. Occurrence {@code n} is always computed from the start date, so dates never drift. The schedule itself
 * (frequency, interval, start date) is fixed; to change it, delete the rule and create a new one.
 */
public class RecurringTransaction {

    private static final int MAX_INTERVAL = 99;

    private final UUID id;
    private final UUID ownerId;
    private UUID walletId;
    private UUID categoryId;
    private TransactionType type;
    private Money amount;
    private String note;
    private String withPerson;
    private final Frequency frequency;
    private final int interval;
    private final LocalDate startDate;
    private LocalDate endDate;
    private int generatedCount;
    private LocalDate nextOccurrence;
    private boolean active;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public RecurringTransaction(UUID id, UUID ownerId, UUID walletId, UUID categoryId, TransactionType type,
                                Money amount, String note, String withPerson, Frequency frequency, int interval,
                                LocalDate startDate, LocalDate endDate, int generatedCount, LocalDate nextOccurrence,
                                boolean active, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.walletId = Objects.requireNonNull(walletId);
        this.categoryId = Objects.requireNonNull(categoryId);
        this.type = Objects.requireNonNull(type);
        this.amount = Objects.requireNonNull(amount);
        this.note = note;
        this.withPerson = withPerson;
        this.frequency = Objects.requireNonNull(frequency);
        this.interval = interval;
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = endDate;
        this.generatedCount = generatedCount;
        this.nextOccurrence = nextOccurrence;
        this.active = active;
        this.version = version;
    }

    public static RecurringTransaction create(NewRecurringTransaction spec, Wallet wallet, Category category) {
        if (spec.frequency() == null) {
            throw new DomainException("FREQUENCY_REQUIRED", "Frequency is required");
        }
        if (spec.interval() < 1 || spec.interval() > MAX_INTERVAL) {
            throw new DomainException("INVALID_INTERVAL", "Interval must be between 1 and " + MAX_INTERVAL);
        }
        if (spec.startDate() == null) {
            throw new DomainException("START_DATE_REQUIRED", "Start date is required");
        }
        RecurringTransaction rule = new RecurringTransaction(UUID.randomUUID(), spec.ownerId(), wallet.getId(),
                category.getId(), TransactionType.of(category.getType()), spec.amount(), null, null, spec.frequency(),
                spec.interval(), spec.startDate(), null, 0, spec.startDate(), true, null);
        rule.change(wallet, category, spec.amount(), spec.note(), spec.withPerson());
        rule.changeEndDate(spec.endDate());
        return rule;
    }

    /** Replaces what each generated transaction looks like; does not touch transactions generated before. */
    public void change(Wallet wallet, Category category, Money newAmount, String newNote, String newPerson) {
        if (!wallet.getOwnerId().equals(ownerId) || !category.getOwnerId().equals(ownerId)) {
            throw new DomainException("FOREIGN_RESOURCE", "Wallet and category must belong to the same user");
        }
        wallet.requireUsable();
        wallet.requireCurrency(newAmount);
        Money validAmount = Texts.positiveAmount(newAmount);
        walletId = wallet.getId();
        categoryId = category.getId();
        type = TransactionType.of(category.getType());
        amount = validAmount;
        note = Texts.optional(newNote);
        withPerson = Texts.optional(newPerson);
    }

    public void changeEndDate(LocalDate newEndDate) {
        if (newEndDate != null && newEndDate.isBefore(startDate)) {
            throw new DomainException("INVALID_END_DATE", "End date must not be before the start date");
        }
        endDate = newEndDate;
        if (nextOccurrence != null && endDate != null && nextOccurrence.isAfter(endDate)) {
            finish();
        }
    }

    public boolean isDue(LocalDate asOf) {
        return active && nextOccurrence != null && !nextOccurrence.isAfter(asOf);
    }

    /** Called after the transaction for {@link #getNextOccurrence()} has been recorded. */
    public void markGenerated() {
        generatedCount++;
        nextOccurrence = frequency.add(startDate, (long) generatedCount * interval);
        if (endDate != null && nextOccurrence.isAfter(endDate)) {
            finish();
        }
    }

    public void pause() {
        active = false;
    }

    /** Resumes without back-filling: occurrences missed while paused are skipped. */
    public void resume(LocalDate today) {
        if (active || nextOccurrence == null) {
            return;
        }
        active = true;
        while (nextOccurrence != null && nextOccurrence.isBefore(today)) {
            markGenerated();
        }
    }

    private void finish() {
        nextOccurrence = null;
        active = false;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getWalletId() { return walletId; }
    public UUID getCategoryId() { return categoryId; }
    public TransactionType getType() { return type; }
    public Money getAmount() { return amount; }
    public String getNote() { return note; }
    public String getWithPerson() { return withPerson; }
    public Frequency getFrequency() { return frequency; }
    public int getInterval() { return interval; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getGeneratedCount() { return generatedCount; }
    public LocalDate getNextOccurrence() { return nextOccurrence; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }

    /** Input for a new recurring transaction rule. */
    public record NewRecurringTransaction(UUID ownerId, Money amount, String note, String withPerson,
                                          Frequency frequency, int interval, LocalDate startDate,
                                          LocalDate endDate) {
    }
}
