package com.financemanage.loan.infrastructure.web;

import com.financemanage.loan.application.SchedulePreview;
import com.financemanage.loan.domain.model.Installment;
import com.financemanage.loan.domain.model.InstallmentStatus;
import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import com.financemanage.loan.domain.model.LoanType;
import com.financemanage.loan.domain.model.NewLoan;
import com.financemanage.loan.domain.model.Repayment;
import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.loan.domain.model.RepaymentType;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Request/response contracts of the loan REST API (v1). */
final class LoanDtos {

    private LoanDtos() {
    }

    record CreateLoanRequest(
            @NotBlank @Size(max = 120) String counterpartyName,
            @NotNull LoanDirection direction,
            LoanType type,
            @NotNull @Positive BigDecimal principal,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @DecimalMin("0") @DecimalMax("100") BigDecimal annualRate,
            @NotNull @Min(1) @Max(600) Integer termMonths,
            @NotNull LocalDate startDate,
            @NotNull RepaymentMethod repaymentMethod,
            @DecimalMin("0") @DecimalMax("100") BigDecimal earlyRepaymentFeeRate,
            @Size(max = 500) String note) {

        NewLoan toSpec(UUID ownerId) {
            return new NewLoan(ownerId, counterpartyName, direction, type,
                    Money.of(principal, currency == null ? "VND" : currency),
                    annualRate == null ? null : Percentage.of(annualRate), termMonths, startDate, repaymentMethod,
                    earlyRepaymentFeeRate == null ? null : Percentage.of(earlyRepaymentFeeRate), note);
        }
    }

    record UpdateLoanRequest(@Size(max = 120) String counterpartyName, @Size(max = 500) String note) {
    }

    record RepaymentRequest(@NotNull @Positive BigDecimal amount, LocalDate paidOn) {
    }

    record PrepaymentRequest(@NotNull @Positive BigDecimal principal, LocalDate paidOn) {
    }

    record RateChangeRequest(@NotNull LocalDate effectiveFrom,
                             @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal annualRate) {
    }

    record InstallmentResponse(UUID id, int periodNo, LocalDate dueDate, BigDecimal principalDue,
                               BigDecimal interestDue, BigDecimal totalDue, BigDecimal principalPaid,
                               BigDecimal interestPaid, BigDecimal remaining, InstallmentStatus status) {

        static InstallmentResponse from(Installment i, LocalDate today) {
            return new InstallmentResponse(i.getId(), i.getPeriodNo(), i.getDueDate(), plain(i.getPrincipalDue()),
                    plain(i.getInterestDue()), plain(i.totalDue()), plain(i.getPrincipalPaid()),
                    plain(i.getInterestPaid()), plain(i.remaining()), i.status(today));
        }
    }

    record RepaymentResponse(UUID id, RepaymentType type, LocalDate paidOn, BigDecimal amount,
                             BigDecimal principalPortion, BigDecimal interestPortion, BigDecimal feePortion) {

        static RepaymentResponse from(Repayment r) {
            return new RepaymentResponse(r.id(), r.type(), r.paidOn(), plain(r.amount()), plain(r.principalPortion()),
                    plain(r.interestPortion()), plain(r.feePortion()));
        }
    }

    record RatePeriodResponse(LocalDate effectiveFrom, BigDecimal annualRate) {
    }

    record LoanSummaryResponse(UUID id, String counterpartyName, LoanDirection direction, LoanType type,
                               BigDecimal principal, String currency, BigDecimal currentRate, int termMonths,
                               LocalDate startDate, RepaymentMethod repaymentMethod, LoanStatus status,
                               BigDecimal outstandingPrincipal, LocalDate nextDueDate, BigDecimal nextDueAmount,
                               int overdueInstallments, String note) {

        static LoanSummaryResponse from(Loan loan, LocalDate today) {
            Optional<Installment> next = loan.nextInstallment();
            return new LoanSummaryResponse(loan.getId(), loan.getCounterpartyName(), loan.getDirection(),
                    loan.getType(), plain(loan.getPrincipal()), loan.getPrincipal().currencyCode(),
                    loan.currentRate(today).normalized(), loan.getTermMonths(), loan.getStartDate(),
                    loan.getRepaymentMethod(), loan.getStatus(), plain(loan.outstandingPrincipal()),
                    next.map(Installment::getDueDate).orElse(null),
                    next.map(i -> plain(i.remaining())).orElse(null), loan.overdueInstallments(today).size(),
                    loan.getNote());
        }
    }

    record LoanDetailResponse(LoanSummaryResponse summary, BigDecimal earlyRepaymentFeeRate,
                              BigDecimal totalScheduledInterest, List<RatePeriodResponse> ratePeriods,
                              List<InstallmentResponse> installments, List<RepaymentResponse> repayments) {

        static LoanDetailResponse from(Loan loan, LocalDate today) {
            return new LoanDetailResponse(LoanSummaryResponse.from(loan, today),
                    loan.getEarlyRepaymentFeeRate().normalized(), plain(loan.totalScheduledInterest()),
                    loan.getRatePeriods().stream()
                            .map(p -> new RatePeriodResponse(p.effectiveFrom(), p.annualRate().normalized())).toList(),
                    loan.getInstallments().stream().map(i -> InstallmentResponse.from(i, today)).toList(),
                    loan.getRepayments().stream().map(RepaymentResponse::from).toList());
        }
    }

    record SchedulePreviewResponse(List<InstallmentResponse> installments, BigDecimal totalInterest,
                                   BigDecimal totalPayment, String currency) {

        static SchedulePreviewResponse from(SchedulePreview preview, LocalDate today) {
            return new SchedulePreviewResponse(
                    preview.installments().stream().map(i -> InstallmentResponse.from(i, today)).toList(),
                    plain(preview.totalInterest()), plain(preview.totalPayment()),
                    preview.totalPayment().currencyCode());
        }
    }

    static BigDecimal plain(Money money) {
        return money == null ? null : money.roundedAmount();
    }
}
