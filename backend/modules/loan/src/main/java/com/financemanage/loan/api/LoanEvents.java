package com.financemanage.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events published by the loan module. */
public final class LoanEvents {

    private LoanEvents() {
    }

    public record LoanCreated(UUID loanId, UUID ownerId, BigDecimal principal, String currency) {
    }

    public record LoanChanged(UUID loanId, UUID ownerId) {
    }

    public record RepaymentRecorded(UUID loanId, UUID ownerId, BigDecimal amount, BigDecimal outstandingPrincipal,
                                    String currency) {
    }

    public record LoanPaidOff(UUID loanId, UUID ownerId, String counterpartyName) {
    }

    public record InstallmentDueSoon(UUID loanId, UUID ownerId, String counterpartyName, boolean borrowed,
                                     LocalDate dueDate, BigDecimal amount, String currency) {
    }

    public record InstallmentOverdue(UUID loanId, UUID ownerId, String counterpartyName, boolean borrowed,
                                     LocalDate dueDate, BigDecimal amount, String currency) {
    }
}
