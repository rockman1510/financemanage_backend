package com.financemanage.reporting.application;

import com.financemanage.shared.domain.CashFlowItem;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cached (also in Redis), hence {@link Serializable}. */
public record Dashboard(
        LocalDate asOf,
        List<CurrencyTotals> totals,
        int activeDeposits,
        int activeLoans,
        int overdueInstallments,
        List<UpcomingItem> upcoming,
        List<MonthlyCashFlow> monthlyCashFlow) implements Serializable {

    /** Net worth = deposits + accrued interest + money lent − money borrowed (per currency). */
    public record CurrencyTotals(String currency, BigDecimal deposits, BigDecimal accruedInterest,
                                 BigDecimal lentOutstanding, BigDecimal borrowedOutstanding, BigDecimal netWorth)
            implements Serializable {
    }

    /** A future cash movement, flattened for API clients. */
    public record UpcomingItem(LocalDate date, BigDecimal amount, String currency, CashFlowItem.Direction direction,
                               String sourceType, UUID sourceId, String title)
            implements Serializable {

        static UpcomingItem from(CashFlowItem item) {
            return new UpcomingItem(item.date(), item.amount().roundedAmount(), item.amount().currencyCode(),
                    item.direction(), item.sourceType(), item.sourceId(), item.title());
        }
    }

    public record MonthlyCashFlow(String month, String currency, BigDecimal inflow, BigDecimal outflow,
                                  BigDecimal net) implements Serializable {
    }
}
