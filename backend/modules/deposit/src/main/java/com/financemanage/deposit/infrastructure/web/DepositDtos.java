package com.financemanage.deposit.infrastructure.web;

import com.financemanage.deposit.application.DepositPreview;
import com.financemanage.deposit.application.DepositView;
import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.deposit.domain.model.DepositStatus;
import com.financemanage.deposit.domain.model.DepositTransactionType;
import com.financemanage.deposit.domain.model.DepositType;
import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.deposit.domain.model.InterestPaymentMethod;
import com.financemanage.deposit.domain.model.NewDeposit;
import com.financemanage.deposit.domain.model.RolloverPolicy;
import com.financemanage.deposit.domain.model.SettlementQuote;
import com.financemanage.deposit.domain.model.WalletLinkStatus;
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
import java.util.UUID;

/** Request/response contracts of the deposit REST API (v1). */
final class DepositDtos {

    private DepositDtos() {
    }

    record CreateDepositRequest(
            @NotBlank @Size(max = 120) String institutionName,
            @Size(max = 64) String accountNumber,
            @NotNull DepositType type,
            @NotNull @Positive BigDecimal principal,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal annualRate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal earlyWithdrawalRate,
            @Min(1) @Max(600) Integer termMonths,
            @NotNull LocalDate openDate,
            InterestPaymentMethod interestPaymentMethod,
            RolloverPolicy rolloverPolicy,
            InterestCalculationMethod calculationMethod,
            @Size(max = 500) String note,
            UUID fromWalletId) {

        NewDeposit toSpec(UUID ownerId) {
            return new NewDeposit(ownerId, institutionName, accountNumber, type,
                    Money.of(principal, currency == null ? "VND" : currency), Percentage.of(annualRate),
                    earlyWithdrawalRate == null ? null : Percentage.of(earlyWithdrawalRate), termMonths, openDate,
                    interestPaymentMethod, rolloverPolicy, calculationMethod, note);
        }
    }

    record UpdateDepositRequest(@Size(max = 120) String institutionName, @Size(max = 64) String accountNumber,
                                @Size(max = 500) String note) {
    }

    /** {@code toWalletId}: optional wallet that receives the settled amount. */
    record SettleDepositRequest(LocalDate date, UUID toWalletId) {
    }

    record TransactionResponse(UUID id, DepositTransactionType type, BigDecimal amount, LocalDate occurredOn) {
    }

    record DepositResponse(
            UUID id, String institutionName, String accountNumber, DepositType type, BigDecimal principal,
            String currency, BigDecimal annualRate, BigDecimal earlyWithdrawalRate, Integer termMonths,
            LocalDate openDate, LocalDate maturityDate, InterestPaymentMethod interestPaymentMethod,
            RolloverPolicy rolloverPolicy, InterestCalculationMethod calculationMethod, DepositStatus status,
            String note, LocalDate settledOn, BigDecimal settledAmount, BigDecimal accruedInterest,
            BigDecimal expectedInterestAtMaturity, List<TransactionResponse> transactions, UUID fundingWalletId,
            WalletLinkStatus fundingStatus, UUID payoutWalletId, WalletLinkStatus payoutStatus) {

        static DepositResponse from(DepositView view) {
            Deposit d = view.deposit();
            return new DepositResponse(d.getId(), d.getInstitutionName(), d.getAccountNumber(), d.getType(),
                    plain(d.getPrincipal()), d.getPrincipal().currencyCode(), d.getAnnualRate().normalized(),
                    d.getEarlyWithdrawalRate().normalized(), d.getTermMonths(), d.getOpenDate(), d.getMaturityDate(),
                    d.getInterestPaymentMethod(), d.getRolloverPolicy(), d.getCalculationMethod(), d.getStatus(),
                    d.getNote(), d.getSettledOn(), plain(d.getSettledAmount()), plain(view.accruedInterest()),
                    plain(view.expectedInterestAtMaturity()),
                    d.getTransactions().stream()
                            .map(t -> new TransactionResponse(t.id(), t.type(), plain(t.amount()), t.occurredOn()))
                            .toList(), d.getFundingWalletId(), d.getFundingStatus(), d.getPayoutWalletId(),
                    d.getPayoutStatus());
        }
    }

    record SettlementQuoteResponse(LocalDate date, BigDecimal principal, BigDecimal interest, BigDecimal total,
                                   boolean early, BigDecimal interestLostByEarlyWithdrawal, String currency) {

        static SettlementQuoteResponse from(SettlementQuote q) {
            return new SettlementQuoteResponse(q.date(), plain(q.principal()), plain(q.interest()), plain(q.total()),
                    q.early(), plain(q.interestLostByEarlyWithdrawal()), q.total().currencyCode());
        }
    }

    record DepositPreviewResponse(LocalDate maturityDate, BigDecimal expectedInterest, BigDecimal totalAtMaturity,
                                  String currency) {

        static DepositPreviewResponse from(DepositPreview p) {
            return new DepositPreviewResponse(p.maturityDate(), plain(p.expectedInterest()),
                    plain(p.totalAtMaturity()), p.totalAtMaturity().currencyCode());
        }
    }

    static BigDecimal plain(Money money) {
        return money == null ? null : money.roundedAmount();
    }
}
