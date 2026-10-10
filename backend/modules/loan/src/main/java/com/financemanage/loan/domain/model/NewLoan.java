package com.financemanage.loan.domain.model;

import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import java.util.UUID;

public record NewLoan(
        UUID ownerId,
        String counterpartyName,
        LoanDirection direction,
        LoanType type,
        Money principal,
        Percentage annualRate,
        int termMonths,
        LocalDate startDate,
        RepaymentMethod repaymentMethod,
        Percentage earlyRepaymentFeeRate,
        String note) {
}
