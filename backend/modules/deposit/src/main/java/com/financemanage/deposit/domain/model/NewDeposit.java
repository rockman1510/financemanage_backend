package com.financemanage.deposit.domain.model;

import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import java.util.UUID;

/** Input for opening a new deposit. */
public record NewDeposit(
        UUID ownerId,
        String institutionName,
        String accountNumber,
        DepositType type,
        Money principal,
        Percentage annualRate,
        Percentage earlyWithdrawalRate,
        Integer termMonths,
        LocalDate openDate,
        InterestPaymentMethod interestPaymentMethod,
        RolloverPolicy rolloverPolicy,
        InterestCalculationMethod calculationMethod,
        String note) {
}
