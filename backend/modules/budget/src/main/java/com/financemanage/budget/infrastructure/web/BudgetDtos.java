package com.financemanage.budget.infrastructure.web;

import com.financemanage.budget.application.BudgetChanges;
import com.financemanage.budget.application.BudgetInput;
import com.financemanage.budget.application.BudgetView;
import com.financemanage.budget.domain.model.Budget;
import com.financemanage.budget.domain.model.BudgetPeriod;
import com.financemanage.budget.domain.model.BudgetState;
import com.financemanage.budget.domain.model.BudgetStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Request/response contracts of the budget REST API (v1). */
final class BudgetDtos {

    private BudgetDtos() {
    }

    record CreateBudgetRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @Positive BigDecimal amount,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            Set<UUID> categoryIds,
            UUID walletId,
            @NotNull BudgetPeriod period,
            LocalDate startDate,
            LocalDate endDate,
            Boolean repeat) {

        BudgetInput toInput() {
            return new BudgetInput(name, amount, currency, categoryIds, walletId, period, startDate, endDate, repeat);
        }
    }

    /** Omitted fields stay unchanged; {@code allWallets=true} removes the wallet filter. */
    record UpdateBudgetRequest(@Size(max = 60) String name, @Positive BigDecimal amount, Set<UUID> categoryIds,
                               UUID walletId, Boolean allWallets, Boolean repeat) {

        BudgetChanges toChanges() {
            return new BudgetChanges(name, amount, categoryIds, walletId, allWallets, repeat);
        }
    }

    /** Status of the period containing the requested date (today by default). */
    record StatusResponse(LocalDate periodStart, LocalDate periodEnd, BigDecimal spent, BigDecimal remaining,
                          int percent, BigDecimal dailySuggestion, long daysLeft, BudgetState state,
                          Set<String> unconvertedCurrencies) {
    }

    record BudgetResponse(UUID id, String name, BigDecimal amount, String currency, Set<UUID> categoryIds,
                          UUID walletId, BudgetPeriod period, LocalDate startDate, LocalDate endDate, boolean repeat,
                          StatusResponse current) {

        static BudgetResponse from(BudgetView view) {
            Budget b = view.budget();
            BudgetStatus s = view.status();
            return new BudgetResponse(b.getId(), b.getName(), b.getAmount().roundedAmount(),
                    b.getAmount().currencyCode(), b.getCategoryIds(), b.getWalletId(), b.getPeriod(),
                    b.getStartDate(), b.getEndDate(), b.isRepeat(),
                    new StatusResponse(s.period() == null ? null : s.period().start(),
                            s.period() == null ? null : s.period().end(), s.spent().roundedAmount(),
                            s.remaining().roundedAmount(), s.percent(), s.dailySuggestion().roundedAmount(),
                            s.daysLeft(), s.state(), view.unconvertedCurrencies()));
        }
    }
}
