package com.financemanage.loan.api;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

public record LoanPosition(UUID loanId, String counterpartyName, boolean borrowed, Money outstandingPrincipal,
                           LocalDate nextDueDate, int overdueInstallments) {
}
