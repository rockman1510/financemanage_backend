package com.financemanage.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.financemanage.deposit.api.DepositPosition;
import com.financemanage.deposit.api.DepositQueryApi;
import com.financemanage.loan.api.LoanPosition;
import com.financemanage.loan.api.LoanQueryApi;
import com.financemanage.shared.domain.CashFlowItem;
import com.financemanage.shared.domain.CashFlowItem.Direction;
import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardCalculatorTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2025, 3, 10);

    private final DepositQueryApi deposits = mock(DepositQueryApi.class);
    private final LoanQueryApi loans = mock(LoanQueryApi.class);
    private final DashboardCalculator calculator = new DashboardCalculator(deposits, loans, 30, 12);

    private static Money vnd(long amount) {
        return Money.of(amount, "VND");
    }

    private static CashFlowItem flow(LocalDate date, long amount, Direction direction) {
        return new CashFlowItem(date, vnd(amount), direction, "TEST", UUID.randomUUID(), "t");
    }

    @Test
    void aggregatesNetWorthPerCurrency() {
        when(deposits.activePositions(OWNER, TODAY)).thenReturn(List.of(
                new DepositPosition(UUID.randomUUID(), "VCB", vnd(100_000_000), vnd(1_000_000), null),
                new DepositPosition(UUID.randomUUID(), "Chase", Money.of(1_000, "USD"), Money.of(5, "USD"), null)));
        when(loans.activePositions(OWNER, TODAY)).thenReturn(List.of(
                new LoanPosition(UUID.randomUUID(), "VPBank", true, vnd(30_000_000), TODAY, 1),
                new LoanPosition(UUID.randomUUID(), "Bình", false, vnd(5_000_000), TODAY, 2)));

        Dashboard dashboard = calculator.compute(OWNER, TODAY);

        assertThat(dashboard.totals()).hasSize(2);
        Dashboard.CurrencyTotals vnd = dashboard.totals().stream().filter(t -> t.currency().equals("VND"))
                .findFirst().orElseThrow();
        assertThat(vnd.deposits()).isEqualByComparingTo("100000000");
        assertThat(vnd.borrowedOutstanding()).isEqualByComparingTo("30000000");
        assertThat(vnd.lentOutstanding()).isEqualByComparingTo("5000000");
        assertThat(vnd.netWorth()).isEqualByComparingTo("76000000");
        assertThat(dashboard.activeDeposits()).isEqualTo(2);
        assertThat(dashboard.overdueInstallments()).isEqualTo(3);
    }

    @Test
    void buildsUpcomingListAndMonthlyProjection() {
        when(deposits.expectedCashFlows(eq(OWNER), eq(TODAY), any())).thenReturn(List.of(
                flow(LocalDate.of(2025, 3, 20), 50_000_000, Direction.IN),
                flow(LocalDate.of(2025, 7, 1), 10_000_000, Direction.IN)));
        when(loans.expectedCashFlows(eq(OWNER), eq(TODAY), any())).thenReturn(List.of(
                flow(LocalDate.of(2025, 3, 15), 4_000_000, Direction.OUT),
                flow(LocalDate.of(2025, 4, 15), 4_000_000, Direction.OUT)));

        Dashboard dashboard = calculator.compute(OWNER, TODAY);

        assertThat(dashboard.upcoming()).extracting(Dashboard.UpcomingItem::date)
                .containsExactly(LocalDate.of(2025, 3, 15), LocalDate.of(2025, 3, 20));
        assertThat(dashboard.upcoming().get(0).amount()).isEqualByComparingTo("4000000");
        assertThat(dashboard.upcoming().get(0).currency()).isEqualTo("VND");
        assertThat(dashboard.monthlyCashFlow()).extracting(Dashboard.MonthlyCashFlow::month)
                .containsExactly("2025-03", "2025-04", "2025-07");
        assertThat(dashboard.monthlyCashFlow().get(0).net()).isEqualByComparingTo("46000000");
        assertThat(dashboard.monthlyCashFlow().get(1).net()).isEqualByComparingTo("-4000000");
    }

    @Test
    void cacheKeyCombinesOwnerAndDay() {
        assertThat(DashboardCalculator.cacheKey(OWNER, TODAY)).isEqualTo(OWNER + ":2025-03-10");
    }
}
