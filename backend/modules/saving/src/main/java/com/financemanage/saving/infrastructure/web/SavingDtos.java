package com.financemanage.saving.infrastructure.web;

import com.financemanage.saving.domain.model.ContributionFrequency;
import com.financemanage.saving.domain.model.GoalProgress;
import com.financemanage.saving.domain.model.MovementSource;
import com.financemanage.saving.domain.model.MovementStatus;
import com.financemanage.saving.domain.model.MovementType;
import com.financemanage.saving.domain.model.SavingGoal;
import com.financemanage.saving.domain.model.SavingMovement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response contracts of the savings goal REST API (v1). */
final class SavingDtos {

    private SavingDtos() {
    }

    record CreateGoalRequest(@NotBlank @Size(max = 60) String name, @NotNull @Positive BigDecimal target,
                             @Pattern(regexp = "[A-Z]{3}") String currency, LocalDate deadline,
                             @Size(max = 40) String icon) {
    }

    /** Omitted fields stay unchanged; {@code clearDeadline=true} removes the deadline. */
    record UpdateGoalRequest(@Size(max = 60) String name, @Positive BigDecimal target, LocalDate deadline,
                             Boolean clearDeadline, @Size(max = 40) String icon) {
    }

    record MoveRequest(@NotNull @Positive BigDecimal amount, @NotNull UUID walletId, LocalDate date) {
    }

    record AutoContributionRequest(@NotNull @Positive BigDecimal amount, @NotNull UUID walletId,
                                   @NotNull ContributionFrequency frequency, LocalDate startDate) {
    }

    /** E.g. unit 10000: an expense of 47 000 puts 3 000 aside. */
    record RoundUpRequest(@NotNull UUID walletId, @NotNull @Positive BigDecimal unit) {
    }

    record MovementResponse(UUID id, MovementType type, MovementSource source, BigDecimal amount, UUID walletId,
                            LocalDate date, MovementStatus status, UUID transactionId, String failureReason) {

        static MovementResponse from(SavingMovement m) {
            return new MovementResponse(m.getId(), m.getType(), m.getSource(), m.getAmount().roundedAmount(),
                    m.getWalletId(), m.getDate(), m.getStatus(), m.getTransactionId(), m.getFailureReason());
        }
    }

    record AutoContributionResponse(BigDecimal amount, UUID walletId, ContributionFrequency frequency,
                                    LocalDate nextDate) {
    }

    record RoundUpResponse(UUID walletId, BigDecimal unit) {
    }

    /** {@code monthlySuggestion}: what to save each month to reach the target by the deadline. */
    record GoalResponse(UUID id, String name, BigDecimal target, String currency, LocalDate deadline, String icon,
                        BigDecimal saved, int percent, BigDecimal remaining, BigDecimal monthlySuggestion,
                        Long daysLeft, boolean reached, AutoContributionResponse autoContribution,
                        RoundUpResponse roundUp, List<MovementResponse> movements) {

        static GoalResponse from(SavingGoal g, LocalDate today) {
            GoalProgress p = g.progress(today);
            return new GoalResponse(g.getId(), g.getName(), g.getTarget().roundedAmount(),
                    g.getTarget().currencyCode(), g.getDeadline(), g.getIcon(), g.getSaved().roundedAmount(),
                    p.percent(), p.remaining().roundedAmount(),
                    p.monthlySuggestion() == null ? null : p.monthlySuggestion().roundedAmount(), p.daysLeft(),
                    p.reached(),
                    g.getAutoFrequency() == null ? null : new AutoContributionResponse(
                            g.getAutoAmount().roundedAmount(), g.getAutoWalletId(), g.getAutoFrequency(),
                            g.nextAutoDate()),
                    g.getRoundUpWalletId() == null ? null : new RoundUpResponse(g.getRoundUpWalletId(),
                            plain(g.getRoundUpUnit())),
                    g.getMovements().reversed().stream().map(MovementResponse::from).toList());
        }
    }

    static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
