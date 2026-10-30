package com.financemanage.saving.application;

import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.saving.api.SavingEvents.SavingGoalChanged;
import com.financemanage.saving.api.SavingEvents.SavingGoalMilestone;
import com.financemanage.saving.api.SavingQueryApi;
import com.financemanage.saving.domain.SavingGoalRepository;
import com.financemanage.saving.domain.model.ContributionFrequency;
import com.financemanage.saving.domain.model.MovementSource;
import com.financemanage.saving.domain.model.MovementType;
import com.financemanage.saving.domain.model.SavingGoal;
import com.financemanage.saving.domain.model.SavingMovement;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Savings goal use cases. Moving money in or out of a goal changes the goal at once and asks the ledger, through a
 * {@link RecordExternalTransaction} command, to record the wallet side (EXTERNAL_OUT / EXTERNAL_IN, never an expense);
 * the ledger's answer confirms the movement or undoes it (saga). Also serves the module's {@link SavingQueryApi}.
 */
@Service
@Transactional
public class SavingService implements SavingQueryApi {

    static final String SOURCE = "SAVING";
    private static final int MAX_CATCH_UP = 53;
    private static final Logger log = LoggerFactory.getLogger(SavingService.class);

    private final SavingGoalRepository goals;
    private final LedgerQueryApi ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SavingService(SavingGoalRepository goals, LedgerQueryApi ledger, ApplicationEventPublisher events,
                         Clock clock) {
        this.goals = goals;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    public SavingGoal create(UUID ownerId, String name, BigDecimal target, String currency, LocalDate deadline,
                             String icon) {
        if (target == null) {
            throw new DomainException("INVALID_AMOUNT", "Target amount is required");
        }
        return goals.save(SavingGoal.create(ownerId, name, Money.of(target, currency == null ? "VND" : currency),
                deadline, icon));
    }

    @Transactional(readOnly = true)
    public List<SavingGoal> list(UUID ownerId) {
        return goals.findByOwner(ownerId);
    }

    @Transactional(readOnly = true)
    public SavingGoal get(UUID id, UUID ownerId) {
        return load(id, ownerId);
    }

    public SavingGoal update(UUID id, UUID ownerId, String name, BigDecimal target, LocalDate deadline,
                             boolean clearDeadline, String icon) {
        SavingGoal goal = load(id, ownerId);
        goal.update(name, target, deadline, clearDeadline, icon);
        return goals.save(goal);
    }

    /** Only an empty goal can be deleted: withdraw what it holds first. */
    public void delete(UUID id, UUID ownerId) {
        SavingGoal goal = load(id, ownerId);
        if (!goal.isEmpty()) {
            throw new DomainException("GOAL_NOT_EMPTY", "Withdraw the saved money before deleting the goal");
        }
        goals.delete(goal);
    }

    public SavingMovement deposit(UUID id, UUID ownerId, BigDecimal amount, UUID walletId, LocalDate date) {
        return move(load(id, ownerId), MovementType.DEPOSIT, MovementSource.MANUAL, amount, walletId, date);
    }

    public SavingMovement withdraw(UUID id, UUID ownerId, BigDecimal amount, UUID walletId, LocalDate date) {
        return move(load(id, ownerId), MovementType.WITHDRAWAL, MovementSource.MANUAL, amount, walletId, date);
    }

    public SavingGoal setAutoContribution(UUID id, UUID ownerId, BigDecimal amount, UUID walletId,
                                          ContributionFrequency frequency, LocalDate startDate) {
        SavingGoal goal = load(id, ownerId);
        WalletBalance wallet = usableWallet(ownerId, walletId, goal);
        goal.setAutoContribution(amount == null ? null : new Money(amount, wallet.balance().currency()), walletId,
                frequency, startDate != null ? startDate : today());
        SavingGoal saved = goals.save(goal);
        runAutoContribution(saved, today()); // a start date today or in the past contributes right away
        return load(id, ownerId);
    }

    public SavingGoal stopAutoContribution(UUID id, UUID ownerId) {
        SavingGoal goal = load(id, ownerId);
        goal.stopAutoContribution();
        return goals.save(goal);
    }

    /** Rounds the wallet's expenses up to {@code unit} and saves the difference; one goal per wallet. */
    public SavingGoal setRoundUp(UUID id, UUID ownerId, UUID walletId, BigDecimal unit) {
        SavingGoal goal = load(id, ownerId);
        usableWallet(ownerId, walletId, goal);
        if (goals.findRoundingUp(ownerId, walletId).stream().anyMatch(g -> !g.getId().equals(id))) {
            throw new DomainException("ROUND_UP_TAKEN", "Another goal already rounds up this wallet's expenses");
        }
        goal.setRoundUp(walletId, unit);
        return goals.save(goal);
    }

    public SavingGoal stopRoundUp(UUID id, UUID ownerId) {
        SavingGoal goal = load(id, ownerId);
        goal.stopRoundUp();
        return goals.save(goal);
    }

    /** Daily job: goals with an automatic contribution due (the job runs them one per call to keep failures apart). */
    @Transactional(readOnly = true)
    public List<SavingGoal> goalsWithDueAutoContribution(LocalDate today) {
        return goals.findWithAutoContribution().stream().filter(g -> g.isAutoDue(today)).toList();
    }

    public void runAutoContribution(UUID goalId, UUID ownerId, LocalDate today) {
        goals.findByIdAndOwner(goalId, ownerId).ifPresent(goal -> runAutoContribution(goal, today));
    }

    /** An expense was recorded in a wallet: goals rounding up that wallet save the difference. */
    public void onExpense(TransactionSnapshot expense) {
        for (SavingGoal goal : goals.findRoundingUp(expense.ownerId(), expense.walletId())) {
            Money extra = goal.roundUpOf(Money.of(expense.amount(), expense.currency()));
            if (extra.isPositive()) {
                try {
                    move(goal, MovementType.DEPOSIT, MovementSource.ROUND_UP, extra.amount(), expense.walletId(),
                            expense.occurredOn());
                } catch (DomainException e) {
                    log.info("Round-up for goal {} skipped: {}", goal.getId(), e.getMessage());
                }
            }
        }
    }

    public void movementRecorded(UUID movementId, UUID transactionId) {
        goals.findByMovement(movementId).ifPresent(goal -> {
            goal.movementRecorded(movementId, transactionId);
            int milestone = goal.newMilestone();
            goals.save(goal);
            events.publishEvent(new SavingGoalChanged(goal.getId(), goal.getOwnerId()));
            if (milestone > 0) {
                events.publishEvent(new SavingGoalMilestone(goal.getId(), goal.getOwnerId(), goal.getName(),
                        milestone, goal.getSaved().roundedAmount(), goal.getTarget().roundedAmount(),
                        goal.getTarget().currencyCode()));
            }
        });
    }

    public void movementRejected(UUID movementId, String reason) {
        goals.findByMovement(movementId).ifPresent(goal -> {
            goal.movementFailed(movementId, reason);
            goals.save(goal);
        });
    }

    public void deleteAllOf(UUID ownerId) {
        goals.deleteByOwner(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedAmount> savedAmounts(UUID ownerId) {
        return goals.findByOwner(ownerId).stream()
                .map(g -> new SavedAmount(g.getId(), g.getName(), g.getSaved())).toList();
    }

    /** Every contribution due up to today (a start date in the past catches up, at most a year of them). */
    private void runAutoContribution(SavingGoal goal, LocalDate today) {
        int made = 0;
        try {
            while (goal.isAutoDue(today) && made < MAX_CATCH_UP) {
                LocalDate date = goal.nextAutoDate();
                goal.autoContributed();
                move(goal, MovementType.DEPOSIT, MovementSource.AUTO, goal.getAutoAmount().amount(),
                        goal.getAutoWalletId(), date);
                made++;
            }
        } catch (DomainException e) {
            log.info("Automatic contribution of goal {} stopped: {}", goal.getId(), e.getMessage());
            SavingGoal fresh = load(goal.getId(), goal.getOwnerId());
            fresh.stopAutoContribution(); // e.g. the wallet was archived: the user sets it up again
            goals.save(fresh);
        }
    }

    private SavingMovement move(SavingGoal goal, MovementType type, MovementSource source, BigDecimal amount,
                                UUID walletId, LocalDate date) {
        WalletBalance wallet = usableWallet(goal.getOwnerId(), walletId, goal);
        Money money = amount == null ? null : new Money(amount, wallet.balance().currency());
        LocalDate day = date != null ? date : today();
        SavingMovement movement = type == MovementType.DEPOSIT
                ? goal.deposit(money, walletId, day, source, clock.instant())
                : goal.withdraw(money, walletId, day, clock.instant());
        goals.save(goal);
        events.publishEvent(new RecordExternalTransaction(movement.getId(), SOURCE, movement.getId(),
                goal.getOwnerId(), walletId, type == MovementType.WITHDRAWAL, null, money.amount(),
                money.currencyCode(), day, (type == MovementType.DEPOSIT ? "Tiết kiệm: " : "Rút tiết kiệm: ")
                + goal.getName()));
        return movement;
    }

    /** The wallet must belong to the owner, not be archived and hold the goal's currency. */
    private WalletBalance usableWallet(UUID ownerId, UUID walletId, SavingGoal goal) {
        if (walletId == null) {
            throw new DomainException("WALLET_REQUIRED", "Wallet is required");
        }
        WalletBalance wallet = ledger.walletBalances(ownerId).stream().filter(w -> w.walletId().equals(walletId))
                .findFirst().orElseThrow(() -> new NotFoundException("Wallet", walletId));
        if (wallet.archived()) {
            throw new DomainException("WALLET_ARCHIVED", "Wallet " + wallet.name() + " is archived");
        }
        if (!wallet.balance().currency().equals(goal.getTarget().currency())) {
            throw new DomainException("CURRENCY_MISMATCH", "The goal is in " + goal.getTarget().currencyCode());
        }
        return wallet;
    }

    private SavingGoal load(UUID id, UUID ownerId) {
        return goals.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("SavingGoal", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
