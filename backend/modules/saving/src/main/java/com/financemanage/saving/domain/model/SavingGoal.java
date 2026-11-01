package com.financemanage.saving.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Savings goal aggregate (mục tiêu tiết kiệm), e.g. "Mua xe – 50 triệu – 12/2027". Its saved amount changes with each
 * movement as soon as it is requested; a movement the ledger refuses is undone. Pure Java so business rules are easy
 * to test.
 */
public class SavingGoal {

    private static final int MAX_NAME_LENGTH = 60;

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private Money target;
    private LocalDate deadline;
    private String icon;
    private Money saved;
    /** Highest milestone already announced: 0, 50 or 100. */
    private int milestone;
    private Money autoAmount;
    private UUID autoWalletId;
    private ContributionFrequency autoFrequency;
    private LocalDate autoStart;
    private int autoCount;
    private UUID roundUpWalletId;
    private BigDecimal roundUpUnit;
    private final List<SavingMovement> movements;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public SavingGoal(UUID id, UUID ownerId, String name, Money target, LocalDate deadline, String icon, Money saved,
                      int milestone, Money autoAmount, UUID autoWalletId, ContributionFrequency autoFrequency,
                      LocalDate autoStart, int autoCount, UUID roundUpWalletId, BigDecimal roundUpUnit,
                      List<SavingMovement> movements, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = name;
        this.target = Objects.requireNonNull(target);
        this.deadline = deadline;
        this.icon = icon;
        this.saved = Objects.requireNonNull(saved);
        this.milestone = milestone;
        this.autoAmount = autoAmount;
        this.autoWalletId = autoWalletId;
        this.autoFrequency = autoFrequency;
        this.autoStart = autoStart;
        this.autoCount = autoCount;
        this.roundUpWalletId = roundUpWalletId;
        this.roundUpUnit = roundUpUnit;
        this.movements = new ArrayList<>(movements == null ? List.of() : movements);
        this.version = version;
    }

    public static SavingGoal create(UUID ownerId, String name, Money target, LocalDate deadline, String icon) {
        return new SavingGoal(UUID.randomUUID(), ownerId, validName(name), validAmount(target), deadline, text(icon),
                Money.zero(target.currency()), 0, null, null, null, null, 0, null, null, List.of(), null);
    }

    /** Partial update: {@code null} values stay unchanged; {@code clearDeadline} removes the deadline. */
    public void update(String newName, BigDecimal newTarget, LocalDate newDeadline, boolean clearDeadline,
                       String newIcon) {
        if (newName != null) {
            name = validName(newName);
        }
        if (newTarget != null) {
            target = validAmount(new Money(newTarget, target.currency()));
            milestone = Math.min(milestone, levelOf(percent()));
        }
        if (clearDeadline) {
            deadline = null;
        } else if (newDeadline != null) {
            deadline = newDeadline;
        }
        if (newIcon != null) {
            icon = text(newIcon);
        }
    }

    public SavingMovement deposit(Money amount, UUID walletId, LocalDate date, MovementSource source, Instant now) {
        requireCurrency(amount);
        SavingMovement movement = new SavingMovement(UUID.randomUUID(), MovementType.DEPOSIT, source,
                validAmount(amount), walletId, date, MovementStatus.PENDING, null, null, now);
        saved = saved.plus(amount);
        movements.add(movement);
        return movement;
    }

    public SavingMovement withdraw(Money amount, UUID walletId, LocalDate date, Instant now) {
        requireCurrency(amount);
        validAmount(amount);
        if (amount.compareTo(saved) > 0) {
            throw new DomainException("INSUFFICIENT_SAVINGS", "The goal holds only " + saved);
        }
        SavingMovement movement = new SavingMovement(UUID.randomUUID(), MovementType.WITHDRAWAL,
                MovementSource.MANUAL, amount, walletId, date, MovementStatus.PENDING, null, null, now);
        saved = saved.minus(amount);
        movements.add(movement);
        return movement;
    }

    public void movementRecorded(UUID movementId, UUID transactionId) {
        movement(movementId).recorded(transactionId);
    }

    /** The ledger refused a movement: it is marked FAILED and its effect on the saved amount is undone. */
    public void movementFailed(UUID movementId, String reason) {
        SavingMovement movement = movement(movementId);
        if (movement.getStatus() != MovementStatus.PENDING) {
            return;
        }
        movement.failed(reason);
        saved = saved.minus(movement.effect());
    }

    /** The milestone (50 or 100) newly reached, or 0; each is reported once. */
    public int newMilestone() {
        int level = levelOf(percent());
        if (level <= milestone) {
            return 0;
        }
        milestone = level;
        return level;
    }

    public GoalProgress progress(LocalDate today) {
        int percent = percent();
        Money remaining = target.minus(saved);
        boolean reached = !remaining.isPositive();
        if (reached) {
            remaining = Money.zero(target.currency());
        }
        Money monthly = null;
        Long daysLeft = null;
        if (deadline != null) {
            daysLeft = Math.max(ChronoUnit.DAYS.between(today, deadline), 0);
            long months = ChronoUnit.MONTHS.between(today, deadline);
            if (today.plusMonths(months).isBefore(deadline)) {
                months++; // a started month counts
            }
            months = Math.max(months, 1);
            monthly = reached ? Money.zero(target.currency())
                    : new Money(remaining.amount().divide(BigDecimal.valueOf(months), 0, RoundingMode.CEILING),
                    target.currency()).rounded();
        }
        return new GoalProgress(percent, remaining, monthly, daysLeft, reached);
    }

    public void setAutoContribution(Money amount, UUID walletId, ContributionFrequency frequency,
                                    LocalDate startDate) {
        requireCurrency(amount);
        if (frequency == null || startDate == null || walletId == null) {
            throw new DomainException("INVALID_AUTO_CONTRIBUTION", "Amount, wallet, frequency and start are required");
        }
        autoAmount = validAmount(amount);
        autoWalletId = walletId;
        autoFrequency = frequency;
        autoStart = startDate;
        autoCount = 0;
    }

    public void stopAutoContribution() {
        autoAmount = null;
        autoWalletId = null;
        autoFrequency = null;
        autoStart = null;
        autoCount = 0;
    }

    /** Next automatic contribution date, null when off. */
    public LocalDate nextAutoDate() {
        return autoFrequency == null ? null : autoFrequency.add(autoStart, autoCount);
    }

    public boolean isAutoDue(LocalDate today) {
        LocalDate next = nextAutoDate();
        return next != null && !next.isAfter(today);
    }

    public void autoContributed() {
        autoCount++;
    }

    /** Round expenses of {@code walletId} up to a multiple of {@code unit} and save the difference. */
    public void setRoundUp(UUID walletId, BigDecimal unit) {
        if (walletId == null || unit == null || unit.signum() <= 0
                || !new Money(unit, target.currency()).hasMinorUnitPrecision()) {
            throw new DomainException("INVALID_ROUND_UP", "A wallet and a positive rounding unit are required");
        }
        roundUpWalletId = walletId;
        roundUpUnit = unit;
    }

    public void stopRoundUp() {
        roundUpWalletId = null;
        roundUpUnit = null;
    }

    /** What rounding {@code expense} up puts aside, e.g. 47 000 with unit 10 000 → 3 000; zero when exact. */
    public Money roundUpOf(Money expense) {
        if (roundUpUnit == null || !expense.currency().equals(target.currency())) {
            return Money.zero(target.currency());
        }
        BigDecimal rounded = expense.amount().divide(roundUpUnit, 0, RoundingMode.CEILING).multiply(roundUpUnit);
        return new Money(rounded.subtract(expense.amount()), target.currency());
    }

    public boolean isEmpty() {
        return saved.isZero() && movements.stream().noneMatch(m -> m.getStatus() == MovementStatus.PENDING);
    }

    private int percent() {
        return saved.amount().multiply(BigDecimal.valueOf(100)).divide(target.amount(), 0, RoundingMode.DOWN)
                .intValue();
    }

    private static int levelOf(int percent) {
        return percent >= 100 ? 100 : percent >= 50 ? 50 : 0;
    }

    private void requireCurrency(Money amount) {
        if (!amount.currency().equals(target.currency())) {
            throw new DomainException("CURRENCY_MISMATCH", "The goal is in " + target.currencyCode());
        }
    }

    private SavingMovement movement(UUID movementId) {
        return movements.stream().filter(m -> m.getId().equals(movementId)).findFirst()
                .orElseThrow(() -> new DomainException("MOVEMENT_NOT_FOUND", "Unknown movement " + movementId));
    }

    private static String validName(String value) {
        String text = text(value);
        if (text == null || text.length() > MAX_NAME_LENGTH) {
            throw new DomainException("GOAL_NAME_REQUIRED", "Goal name is required and at most 60 characters");
        }
        return text;
    }

    private static Money validAmount(Money value) {
        if (value == null || !value.isPositive() || !value.hasMinorUnitPrecision()) {
            throw new DomainException("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        return value;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public Money getTarget() { return target; }
    public LocalDate getDeadline() { return deadline; }
    public String getIcon() { return icon; }
    public Money getSaved() { return saved; }
    public int getMilestone() { return milestone; }
    public Money getAutoAmount() { return autoAmount; }
    public UUID getAutoWalletId() { return autoWalletId; }
    public ContributionFrequency getAutoFrequency() { return autoFrequency; }
    public LocalDate getAutoStart() { return autoStart; }
    public int getAutoCount() { return autoCount; }
    public UUID getRoundUpWalletId() { return roundUpWalletId; }
    public BigDecimal getRoundUpUnit() { return roundUpUnit; }
    public List<SavingMovement> getMovements() { return Collections.unmodifiableList(movements); }
    public Long getVersion() { return version; }
}
