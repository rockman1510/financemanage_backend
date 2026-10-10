package com.financemanage.loan.domain.model;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

/** One entry of the repayment schedule. */
public class Installment {

    private final UUID id;
    private final int periodNo;
    private final LocalDate dueDate;
    private final Money principalDue;
    private final Money interestDue;
    private Money principalPaid;
    private Money interestPaid;

    public Installment(UUID id, int periodNo, LocalDate dueDate, Money principalDue, Money interestDue,
                       Money principalPaid, Money interestPaid) {
        this.id = id;
        this.periodNo = periodNo;
        this.dueDate = dueDate;
        this.principalDue = principalDue;
        this.interestDue = interestDue;
        this.principalPaid = principalPaid;
        this.interestPaid = interestPaid;
    }

    public static Installment planned(int periodNo, LocalDate dueDate, Money principal, Money interest) {
        Money zero = Money.zero(principal.currency());
        return new Installment(UUID.randomUUID(), periodNo, dueDate, principal, interest, zero, zero);
    }

    /** Pays interest first, then principal, using at most {@code amount}. */
    Allocation pay(Money amount) {
        Money interestPart = amount.min(remainingInterest());
        interestPaid = interestPaid.plus(interestPart);
        Money principalPart = amount.minus(interestPart).min(remainingPrincipal());
        principalPaid = principalPaid.plus(principalPart);
        return new Allocation(principalPart, interestPart);
    }

    record Allocation(Money principal, Money interest) {

        Money total() {
            return principal.plus(interest);
        }
    }

    public Money remainingPrincipal() {
        return principalDue.minus(principalPaid);
    }

    public Money remainingInterest() {
        return interestDue.minus(interestPaid);
    }

    public Money remaining() {
        return remainingPrincipal().plus(remainingInterest());
    }

    public Money totalDue() {
        return principalDue.plus(interestDue);
    }

    public boolean isPaid() {
        return remaining().isZero();
    }

    public boolean isUntouched() {
        return principalPaid.isZero() && interestPaid.isZero();
    }

    public InstallmentStatus status(LocalDate asOf) {
        if (isPaid()) {
            return InstallmentStatus.PAID;
        }
        if (dueDate.isBefore(asOf)) {
            return InstallmentStatus.OVERDUE;
        }
        return isUntouched() ? InstallmentStatus.PENDING : InstallmentStatus.PARTIAL;
    }

    public UUID getId() { return id; }
    public int getPeriodNo() { return periodNo; }
    public LocalDate getDueDate() { return dueDate; }
    public Money getPrincipalDue() { return principalDue; }
    public Money getInterestDue() { return interestDue; }
    public Money getPrincipalPaid() { return principalPaid; }
    public Money getInterestPaid() { return interestPaid; }
}
