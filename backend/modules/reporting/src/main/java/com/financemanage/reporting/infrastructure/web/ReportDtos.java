package com.financemanage.reporting.infrastructure.web;

import com.financemanage.reporting.application.NetWorthCalculator.Kind;
import com.financemanage.reporting.application.ReportService;
import com.financemanage.reporting.application.ReportService.Granularity;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response contracts of the reports REST API (v1); amounts are rounded to the currency's minor unit. */
final class ReportDtos {

    private ReportDtos() {
    }

    record TotalsResponse(BigDecimal income, BigDecimal expense, BigDecimal net) {

        static TotalsResponse from(ReportService.Totals t) {
            return new TotalsResponse(amount(t.income()), amount(t.expense()), amount(t.net()));
        }
    }

    record SummaryResponse(LocalDate from, LocalDate to, String currency, BigDecimal income, BigDecimal expense,
                           BigDecimal net, BigDecimal savingsRate, LocalDate previousFrom, LocalDate previousTo,
                           TotalsResponse previous, BigDecimal incomeChange, BigDecimal expenseChange) {

        static SummaryResponse from(ReportService.Summary s) {
            return new SummaryResponse(s.period().from(), s.period().to(), s.currency(), amount(s.totals().income()),
                    amount(s.totals().expense()), amount(s.totals().net()), s.savingsRate(),
                    s.previousPeriod().from(), s.previousPeriod().to(), TotalsResponse.from(s.previous()),
                    s.incomeChange(), s.expenseChange());
        }
    }

    record CategoryResponse(UUID categoryId, String name, BigDecimal amount, BigDecimal percent,
                            List<CategoryResponse> children) {

        static CategoryResponse from(ReportService.CategoryTotal c) {
            return new CategoryResponse(c.categoryId(), c.name(), ReportDtos.amount(c.amount()), c.percent(),
                    c.children().stream().map(CategoryResponse::from).toList());
        }
    }

    record CategoryReportResponse(LocalDate from, LocalDate to, String type, String currency, BigDecimal total,
                                  List<CategoryResponse> categories) {

        static CategoryReportResponse from(ReportService.CategoryReport r) {
            return new CategoryReportResponse(r.period().from(), r.period().to(), r.type(), r.currency(),
                    amount(r.total()), r.categories().stream().map(CategoryResponse::from).toList());
        }
    }

    record CashFlowPointResponse(String period, BigDecimal income, BigDecimal expense, BigDecimal net,
                                 BigDecimal externalIn, BigDecimal externalOut, BigDecimal cashFlow) {
    }

    record CashFlowResponse(LocalDate from, LocalDate to, String currency, Granularity granularity,
                            List<CashFlowPointResponse> points) {

        static CashFlowResponse from(ReportService.CashFlowReport r) {
            return new CashFlowResponse(r.period().from(), r.period().to(), r.currency(), r.granularity(),
                    r.points().stream().map(p -> new CashFlowPointResponse(p.period(), amount(p.income()),
                            amount(p.expense()), amount(p.net()), amount(p.externalIn()), amount(p.externalOut()),
                            amount(p.cashFlow()))).toList());
        }
    }

    record NetWorthItemResponse(UUID id, String name, BigDecimal amount, String currency, BigDecimal converted) {
    }

    record NetWorthGroupResponse(Kind kind, BigDecimal total, List<NetWorthItemResponse> items) {
    }

    record NetWorthResponse(LocalDate asOf, String currency, BigDecimal assets, BigDecimal liabilities,
                            BigDecimal netWorth, List<NetWorthGroupResponse> groups) {

        static NetWorthResponse from(ReportService.NetWorth n) {
            return new NetWorthResponse(n.asOf(), n.currency(), amount(n.assets()), amount(n.liabilities()),
                    amount(n.netWorth()), n.groups().stream().map(g -> new NetWorthGroupResponse(g.kind(),
                    amount(g.total()), g.items().stream().map(i -> new NetWorthItemResponse(i.id(), i.name(),
                    amount(i.original()), i.original().currencyCode(), amount(i.converted()))).toList())).toList());
        }
    }

    static BigDecimal amount(Money money) {
        return money.roundedAmount();
    }
}
