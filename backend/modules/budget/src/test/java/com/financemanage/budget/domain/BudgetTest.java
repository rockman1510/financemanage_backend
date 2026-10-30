package com.financemanage.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.budget.domain.model.Budget;
import com.financemanage.budget.domain.model.Budget.NewBudget;
import com.financemanage.budget.domain.model.BudgetPeriod;
import com.financemanage.budget.domain.model.BudgetState;
import com.financemanage.budget.domain.model.BudgetStatus;
import com.financemanage.budget.domain.model.PeriodRange;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BudgetTest {

    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void monthlyPeriodsStartFromTheStartDateWithoutDrifting() {
        Budget budget = budget(BudgetPeriod.MONTHLY, LocalDate.of(2025, 1, 31), null, true);

        assertThat(budget.periodAt(LocalDate.of(2025, 2, 10))).hasValue(
                new PeriodRange(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 27)));
        assertThat(budget.periodAt(LocalDate.of(2025, 3, 30))).hasValue(
                new PeriodRange(LocalDate.of(2025, 2, 28), LocalDate.of(2025, 3, 30)));
        assertThat(budget.periodAt(LocalDate.of(2025, 3, 31))).hasValue(
                new PeriodRange(LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 29)));
        assertThat(budget.periodAt(LocalDate.of(2025, 1, 30))).isEmpty();
    }

    @Test
    void nonRepeatingAndCustomBudgetsEnd() {
        Budget once = budget(BudgetPeriod.WEEKLY, LocalDate.of(2025, 4, 7), null, false);
        Budget trip = budget(BudgetPeriod.CUSTOM, LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 10), true);

        assertThat(once.periodAt(LocalDate.of(2025, 4, 13))).isPresent();
        assertThat(once.status(LocalDate.of(2025, 4, 14), vnd(0), 80).state()).isEqualTo(BudgetState.ENDED);
        assertThat(trip.periodAt(LocalDate.of(2025, 5, 10))).hasValue(
                new PeriodRange(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 10)));
        assertThat(trip.status(LocalDate.of(2025, 4, 30), vnd(0), 80).state()).isEqualTo(BudgetState.NOT_STARTED);
        assertThatThrownBy(() -> budget(BudgetPeriod.CUSTOM, LocalDate.of(2025, 5, 1), null, true))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void statusSuggestsADailyAmount() {
        Budget food = budget(BudgetPeriod.MONTHLY, LocalDate.of(2025, 4, 1), null, true);

        BudgetStatus status = food.status(LocalDate.of(2025, 4, 21), vnd(4_200_000), 80);

        assertThat(status.remaining()).isEqualTo(vnd(800_000));
        assertThat(status.percent()).isEqualTo(84);
        assertThat(status.daysLeft()).isEqualTo(10);
        assertThat(status.dailySuggestion()).isEqualTo(vnd(80_000));
        assertThat(status.state()).isEqualTo(BudgetState.WARNING);
        BudgetStatus over = food.status(LocalDate.of(2025, 4, 21), vnd(5_100_000), 80);
        assertThat(over.state()).isEqualTo(BudgetState.EXCEEDED);
        assertThat(over.dailySuggestion()).isEqualTo(vnd(0));
    }

    @Test
    void eachAlertLevelIsReportedOncePerPeriod() {
        Budget food = budget(BudgetPeriod.MONTHLY, LocalDate.of(2025, 4, 1), null, true);
        PeriodRange april = food.periodAt(LocalDate.of(2025, 4, 10)).orElseThrow();
        PeriodRange may = food.periodAt(LocalDate.of(2025, 5, 10)).orElseThrow();

        assertThat(food.newAlertLevel(april, 50, 80)).isZero();
        assertThat(food.newAlertLevel(april, 85, 80)).isEqualTo(80);
        assertThat(food.newAlertLevel(april, 90, 80)).isZero();
        assertThat(food.newAlertLevel(april, 120, 80)).isEqualTo(100);
        assertThat(food.newAlertLevel(april, 130, 80)).isZero();
        assertThat(food.newAlertLevel(may, 81, 80)).isEqualTo(80);
    }

    private static Budget budget(BudgetPeriod period, LocalDate start, LocalDate end, boolean repeat) {
        return Budget.create(new NewBudget(OWNER, "Ăn uống", vnd(5_000_000), Set.of(), null, period, start, end,
                repeat));
    }

    private static Money vnd(long amount) {
        return Money.of(amount, "VND");
    }
}
