package com.financemanage.saving.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.saving.domain.model.ContributionFrequency;
import com.financemanage.saving.domain.model.GoalProgress;
import com.financemanage.saving.domain.model.MovementSource;
import com.financemanage.saving.domain.model.MovementStatus;
import com.financemanage.saving.domain.model.SavingGoal;
import com.financemanage.saving.domain.model.SavingMovement;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SavingGoalTest {

    private static final Instant NOW = Instant.parse("2025-04-01T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2025, 4, 1);
    private static final UUID WALLET = UUID.randomUUID();

    @Test
    void depositsAndWithdrawalsChangeTheSavedAmount() {
        SavingGoal car = goal(50_000_000, LocalDate.of(2025, 12, 31));

        car.deposit(vnd(10_000_000), WALLET, TODAY, MovementSource.MANUAL, NOW);
        car.withdraw(vnd(3_000_000), WALLET, TODAY, NOW);

        assertThat(car.getSaved()).isEqualTo(vnd(7_000_000));
        assertThatThrownBy(() -> car.withdraw(vnd(8_000_000), WALLET, TODAY, NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INSUFFICIENT_SAVINGS");
        assertThatThrownBy(() -> car.deposit(Money.of(5, "USD"), WALLET, TODAY, MovementSource.MANUAL, NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void aRefusedMovementIsUndone() {
        SavingGoal car = goal(50_000_000, null);
        SavingMovement in = car.deposit(vnd(10_000_000), WALLET, TODAY, MovementSource.MANUAL, NOW);
        SavingMovement out = car.withdraw(vnd(4_000_000), WALLET, TODAY, NOW);

        car.movementFailed(out.getId(), "WALLET_ARCHIVED");
        car.movementFailed(in.getId(), "WALLET_ARCHIVED");
        car.movementFailed(in.getId(), "again"); // only once

        assertThat(car.getSaved()).isEqualTo(vnd(0));
        assertThat(car.getMovements()).extracting(SavingMovement::getStatus)
                .containsOnly(MovementStatus.FAILED);
    }

    @Test
    void milestonesAreReportedOnce() {
        SavingGoal car = goal(10_000_000, null);

        car.deposit(vnd(4_000_000), WALLET, TODAY, MovementSource.MANUAL, NOW);
        assertThat(car.newMilestone()).isZero();
        car.deposit(vnd(2_000_000), WALLET, TODAY, MovementSource.MANUAL, NOW);
        assertThat(car.newMilestone()).isEqualTo(50);
        assertThat(car.newMilestone()).isZero();
        car.deposit(vnd(5_000_000), WALLET, TODAY, MovementSource.MANUAL, NOW);
        assertThat(car.newMilestone()).isEqualTo(100);
    }

    @Test
    void suggestsAMonthlyAmountUntilTheDeadline() {
        SavingGoal car = goal(50_000_000, LocalDate.of(2025, 12, 15));
        car.deposit(vnd(5_000_000), WALLET, TODAY, MovementSource.MANUAL, NOW);

        GoalProgress progress = car.progress(TODAY);

        assertThat(progress.percent()).isEqualTo(10);
        assertThat(progress.remaining()).isEqualTo(vnd(45_000_000));
        assertThat(progress.monthlySuggestion()).isEqualTo(vnd(5_000_000)); // 45tr / 9 months: Apr … Dec (started)
        assertThat(progress.daysLeft()).isEqualTo(258);
        assertThat(goal(1, null).progress(TODAY).monthlySuggestion()).isNull();
    }

    @Test
    void roundsExpensesUp() {
        SavingGoal trip = goal(10_000_000, null);
        trip.setRoundUp(WALLET, new BigDecimal("10000"));

        assertThat(trip.roundUpOf(vnd(47_000))).isEqualTo(vnd(3_000));
        assertThat(trip.roundUpOf(vnd(50_000))).isEqualTo(vnd(0));
        assertThat(trip.roundUpOf(Money.of(3, "USD"))).isEqualTo(vnd(0));
    }

    @Test
    void automaticContributionsFollowTheirScheduleFromTheStart() {
        SavingGoal trip = goal(10_000_000, null);
        trip.setAutoContribution(vnd(500_000), WALLET, ContributionFrequency.MONTHLY, LocalDate.of(2025, 1, 31));

        assertThat(trip.nextAutoDate()).isEqualTo(LocalDate.of(2025, 1, 31));
        trip.autoContributed();
        assertThat(trip.nextAutoDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        trip.autoContributed();
        assertThat(trip.nextAutoDate()).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(trip.isAutoDue(TODAY)).isTrue();
        trip.stopAutoContribution();
        assertThat(trip.isAutoDue(TODAY)).isFalse();
    }

    private static SavingGoal goal(long target, LocalDate deadline) {
        return SavingGoal.create(UUID.randomUUID(), "Mua xe", vnd(target), deadline, null);
    }

    private static Money vnd(long amount) {
        return Money.of(amount, "VND");
    }
}
