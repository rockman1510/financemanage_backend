package com.financemanage.budget.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Budget aggregate (ngân sách): a spending limit for some expense categories (all when empty), optionally one wallet,
 * over a repeating period (weekly, monthly…) or one custom date range. Spending itself is read from the ledger.
 * Pure Java so business rules are easy to test.
 */
public class Budget {

    private static final int MAX_NAME_LENGTH = 60;

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private Money amount;
    private Set<UUID> categoryIds;
    private UUID walletId;
    private final BudgetPeriod period;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private boolean repeat;
    /** Start of the period of the last alert and the highest level already alerted in it (0, warning or 100). */
    private LocalDate alertPeriodStart;
    private int alertLevel;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Budget(UUID id, UUID ownerId, String name, Money amount, Set<UUID> categoryIds, UUID walletId,
                  BudgetPeriod period, LocalDate startDate, LocalDate endDate, boolean repeat,
                  LocalDate alertPeriodStart, int alertLevel, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = name;
        this.amount = Objects.requireNonNull(amount);
        this.categoryIds = new LinkedHashSet<>(categoryIds == null ? Set.of() : categoryIds);
        this.walletId = walletId;
        this.period = Objects.requireNonNull(period);
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = endDate;
        this.repeat = repeat;
        this.alertPeriodStart = alertPeriodStart;
        this.alertLevel = alertLevel;
        this.version = version;
    }

    public static Budget create(NewBudget spec) {
        if (spec.period() == null) {
            throw new DomainException("PERIOD_REQUIRED", "Budget period is required");
        }
        LocalDate start = spec.startDate();
        LocalDate end = null;
        if (spec.period() == BudgetPeriod.CUSTOM) {
            if (start == null || spec.endDate() == null || spec.endDate().isBefore(start)) {
                throw new DomainException("INVALID_DATE_RANGE", "A custom budget needs a start and an end date");
            }
            end = spec.endDate();
        } else if (start == null) {
            throw new DomainException("START_DATE_REQUIRED", "Start date is required");
        }
        return new Budget(UUID.randomUUID(), spec.ownerId(), validName(spec.name()), validAmount(spec.amount()),
                spec.categoryIds(), spec.walletId(), spec.period(), start, end,
                spec.period() != BudgetPeriod.CUSTOM && spec.repeat(), null, 0, null);
    }

    /** Partial update: {@code null} values stay unchanged. Period and dates never change. */
    public void update(String newName, BigDecimal newAmount, Set<UUID> newCategoryIds, UUID newWalletId,
                       boolean clearWallet, Boolean newRepeat) {
        if (newName != null) {
            name = validName(newName);
        }
        if (newAmount != null) {
            amount = validAmount(new Money(newAmount, amount.currency()));
            alertLevel = 0; // a new limit may need new alerts in the current period
        }
        if (newCategoryIds != null) {
            categoryIds = new LinkedHashSet<>(newCategoryIds);
        }
        if (clearWallet) {
            walletId = null;
        } else if (newWalletId != null) {
            walletId = newWalletId;
        }
        if (newRepeat != null && period != BudgetPeriod.CUSTOM) {
            repeat = newRepeat;
        }
    }

    /** The period containing {@code date}; empty before the start or after the last period. */
    public Optional<PeriodRange> periodAt(LocalDate date) {
        if (date.isBefore(startDate)) {
            return Optional.empty();
        }
        if (period == BudgetPeriod.CUSTOM) {
            return date.isAfter(endDate) ? Optional.empty() : Optional.of(new PeriodRange(startDate, endDate));
        }
        long n = Math.max(period.estimate(startDate, date), 0);
        while (n > 0 && period.start(startDate, n).isAfter(date)) {
            n--;
        }
        while (!period.start(startDate, n + 1).isAfter(date)) {
            n++;
        }
        if (!repeat && n > 0) {
            return Optional.empty();
        }
        return Optional.of(new PeriodRange(period.start(startDate, n), period.start(startDate, n + 1).minusDays(1)));
    }

    /** The status on {@code date} given the spending of the period containing it. */
    public BudgetStatus status(LocalDate date, Money spent, int warningPercent) {
        Optional<PeriodRange> current = periodAt(date);
        if (current.isEmpty()) {
            return BudgetStatus.inactive(amount, date.isBefore(startDate) ? BudgetState.NOT_STARTED
                    : BudgetState.ENDED);
        }
        return BudgetStatus.of(current.get(), amount, spent, date, warningPercent);
    }

    /**
     * The alert level newly reached in {@code range} (warning or 100), or 0 when there is nothing new to tell.
     * Remembers it, so each level is alerted once per period.
     */
    public int newAlertLevel(PeriodRange range, int percent, int warningPercent) {
        int level = percent >= 100 ? 100 : percent >= warningPercent ? warningPercent : 0;
        if (!range.start().equals(alertPeriodStart)) {
            alertPeriodStart = range.start();
            alertLevel = 0;
        }
        if (level <= alertLevel) {
            return 0;
        }
        alertLevel = level;
        return level;
    }

    private static String validName(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.length() > MAX_NAME_LENGTH) {
            throw new DomainException("BUDGET_NAME_REQUIRED", "Budget name is required and at most 60 characters");
        }
        return text;
    }

    private static Money validAmount(Money value) {
        if (value == null || !value.isPositive() || !value.hasMinorUnitPrecision()) {
            throw new DomainException("INVALID_AMOUNT", "Budget amount must be greater than zero");
        }
        return value;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public Money getAmount() { return amount; }
    public Set<UUID> getCategoryIds() { return Collections.unmodifiableSet(categoryIds); }
    public UUID getWalletId() { return walletId; }
    public BudgetPeriod getPeriod() { return period; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isRepeat() { return repeat; }
    public LocalDate getAlertPeriodStart() { return alertPeriodStart; }
    public int getAlertLevel() { return alertLevel; }
    public Long getVersion() { return version; }

    /** Input for a new budget; empty {@code categoryIds} = every expense category. */
    public record NewBudget(UUID ownerId, String name, Money amount, Set<UUID> categoryIds, UUID walletId,
                            BudgetPeriod period, LocalDate startDate, LocalDate endDate, boolean repeat) {
    }
}
