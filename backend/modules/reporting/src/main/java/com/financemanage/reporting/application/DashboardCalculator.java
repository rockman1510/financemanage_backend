package com.financemanage.reporting.application;

import com.financemanage.deposit.api.DepositPosition;
import com.financemanage.deposit.api.DepositQueryApi;
import com.financemanage.loan.api.LoanPosition;
import com.financemanage.loan.api.LoanQueryApi;
import com.financemanage.reporting.application.Dashboard.CurrencyTotals;
import com.financemanage.reporting.application.Dashboard.MonthlyCashFlow;
import com.financemanage.shared.domain.CashFlowItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/** Builds the dashboard. Cached per owner and day; see {@link DashboardCacheEvictor}. */
@Component
public class DashboardCalculator {

    public static final String CACHE = "dashboard";

    private final DepositQueryApi deposits;
    private final LoanQueryApi loans;
    private final int upcomingDays;
    private final int projectionMonths;

    public DashboardCalculator(DepositQueryApi deposits, LoanQueryApi loans,
                               @Value("${app.reporting.upcoming-days:30}") int upcomingDays,
                               @Value("${app.reporting.projection-months:12}") int projectionMonths) {
        this.deposits = deposits;
        this.loans = loans;
        this.upcomingDays = upcomingDays;
        this.projectionMonths = projectionMonths;
    }

    public static String cacheKey(UUID ownerId, LocalDate asOf) {
        return ownerId + ":" + asOf;
    }

    @Cacheable(cacheNames = CACHE, key = "T(com.financemanage.reporting.application.DashboardCalculator)"
            + ".cacheKey(#ownerId, #asOf)")
    public Dashboard compute(UUID ownerId, LocalDate asOf) {
        List<DepositPosition> depositPositions = deposits.activePositions(ownerId, asOf);
        List<LoanPosition> loanPositions = loans.activePositions(ownerId, asOf);

        Map<String, BigDecimal[]> totals = new TreeMap<>(); // deposits, accrued, lent, borrowed
        for (DepositPosition p : depositPositions) {
            BigDecimal[] t = totals.computeIfAbsent(p.principal().currencyCode(), c -> zeros(4));
            t[0] = t[0].add(p.principal().amount());
            t[1] = t[1].add(p.accruedInterest().amount());
        }
        for (LoanPosition p : loanPositions) {
            BigDecimal[] t = totals.computeIfAbsent(p.outstandingPrincipal().currencyCode(), c -> zeros(4));
            int index = p.borrowed() ? 3 : 2;
            t[index] = t[index].add(p.outstandingPrincipal().amount());
        }
        List<CurrencyTotals> currencyTotals = totals.entrySet().stream().map(e -> {
            BigDecimal[] t = e.getValue();
            String c = e.getKey();
            return new CurrencyTotals(c, round(t[0], c), round(t[1], c), round(t[2], c), round(t[3], c),
                    round(t[0].add(t[1]).add(t[2]).subtract(t[3]), c));
        }).toList();

        LocalDate projectionEnd = YearMonth.from(asOf).plusMonths(projectionMonths - 1L).atEndOfMonth();
        List<CashFlowItem> flows = new ArrayList<>(deposits.expectedCashFlows(ownerId, asOf, projectionEnd));
        flows.addAll(loans.expectedCashFlows(ownerId, asOf, projectionEnd));
        flows.sort(Comparator.comparing(CashFlowItem::date));

        List<Dashboard.UpcomingItem> upcoming = flows.stream()
                .filter(f -> !f.date().isAfter(asOf.plusDays(upcomingDays)))
                .map(Dashboard.UpcomingItem::from)
                .toList();

        int overdue = loanPositions.stream().mapToInt(LoanPosition::overdueInstallments).sum();
        return new Dashboard(asOf, currencyTotals, depositPositions.size(), loanPositions.size(), overdue, upcoming,
                monthly(flows));
    }

    private static List<MonthlyCashFlow> monthly(List<CashFlowItem> flows) {
        Map<String, BigDecimal[]> byMonth = new TreeMap<>();
        for (CashFlowItem f : flows) {
            String key = YearMonth.from(f.date()) + "|" + f.amount().currencyCode();
            BigDecimal[] t = byMonth.computeIfAbsent(key, k -> zeros(2));
            int index = f.direction() == CashFlowItem.Direction.IN ? 0 : 1;
            t[index] = t[index].add(f.amount().amount());
        }
        return byMonth.entrySet().stream().map(e -> {
            String[] parts = e.getKey().split("\\|");
            BigDecimal[] t = e.getValue();
            return new MonthlyCashFlow(parts[0], parts[1], round(t[0], parts[1]), round(t[1], parts[1]),
                    round(t[0].subtract(t[1]), parts[1]));
        }).toList();
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] values = new BigDecimal[n];
        Arrays.fill(values, BigDecimal.ZERO);
        return values;
    }

    /** Rounds to the currency minor unit (0 decimals for VND, 2 for USD). */
    private static BigDecimal round(BigDecimal value, String currency) {
        return value.setScale(Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0),
                RoundingMode.HALF_UP);
    }
}
