package com.financemanage.loan.domain.model;

import com.financemanage.loan.domain.policy.RepaymentScheduleGenerator;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Loan aggregate (khoản vay / cho vay). Owns its repayment schedule and repayment history.
 * Framework-free: schedule calculation is delegated to a {@link RepaymentScheduleGenerator} strategy.
 */
public class Loan {

    private static final int MAX_TERM_MONTHS = 600;

    private final UUID id;
    private final UUID ownerId;
    private String counterpartyName;
    private final LoanDirection direction;
    private final LoanType type;
    private final Money principal;
    private final int termMonths;
    private final LocalDate startDate;
    private final RepaymentMethod repaymentMethod;
    private final List<RatePeriod> ratePeriods;
    private final Percentage earlyRepaymentFeeRate;
    private LoanStatus status;
    private String note;
    private final List<Installment> installments;
    private final List<Repayment> repayments;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Loan(UUID id, UUID ownerId, String counterpartyName, LoanDirection direction, LoanType type,
                Money principal, int termMonths, LocalDate startDate, RepaymentMethod repaymentMethod,
                List<RatePeriod> ratePeriods, Percentage earlyRepaymentFeeRate, LoanStatus status, String note,
                List<Installment> installments, List<Repayment> repayments, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.counterpartyName = counterpartyName;
        this.direction = Objects.requireNonNull(direction);
        this.type = Objects.requireNonNull(type);
        this.principal = Objects.requireNonNull(principal);
        this.termMonths = termMonths;
        this.startDate = Objects.requireNonNull(startDate);
        this.repaymentMethod = Objects.requireNonNull(repaymentMethod);
        this.ratePeriods = new ArrayList<>(ratePeriods);
        this.ratePeriods.sort(Comparator.comparing(RatePeriod::effectiveFrom));
        this.earlyRepaymentFeeRate = earlyRepaymentFeeRate == null ? Percentage.zero() : earlyRepaymentFeeRate;
        this.status = Objects.requireNonNull(status);
        this.note = note;
        this.installments = new ArrayList<>(installments);
        this.installments.sort(Comparator.comparingInt(Installment::getPeriodNo));
        this.repayments = new ArrayList<>(repayments);
        this.version = version;
    }

    public static Loan create(NewLoan spec, RepaymentScheduleGenerator generator) {
        if (spec.counterpartyName() == null || spec.counterpartyName().isBlank()) {
            throw new DomainException("COUNTERPARTY_REQUIRED", "Counterparty name is required");
        }
        if (spec.principal() == null || !spec.principal().isPositive()) {
            throw new DomainException("INVALID_PRINCIPAL", "Principal must be greater than zero");
        }
        if (spec.termMonths() < 1 || spec.termMonths() > MAX_TERM_MONTHS) {
            throw new DomainException("INVALID_TERM", "Term must be between 1 and 600 months");
        }
        if (spec.startDate() == null) {
            throw new DomainException("START_DATE_REQUIRED", "Start date is required");
        }
        if (generator.method() != spec.repaymentMethod()) {
            throw new IllegalArgumentException("Generator does not match repayment method");
        }
        Percentage rate = spec.repaymentMethod() == RepaymentMethod.INTEREST_FREE || spec.annualRate() == null
                ? Percentage.zero()
                : spec.annualRate();
        Loan loan = new Loan(UUID.randomUUID(), spec.ownerId(), spec.counterpartyName().trim(), spec.direction(),
                spec.type() == null ? LoanType.PERSONAL : spec.type(), spec.principal(), spec.termMonths(),
                spec.startDate(), spec.repaymentMethod(), List.of(new RatePeriod(spec.startDate(), rate)),
                spec.earlyRepaymentFeeRate(), LoanStatus.ACTIVE, spec.note(), List.of(), List.of(), null);
        loan.installments.addAll(generator.generate(spec.principal(), spec.termMonths(), spec.startDate(), 1,
                loan::rateAt));
        return loan;
    }

    /** Annual rate applicable on {@code date}. */
    public Percentage rateAt(LocalDate date) {
        Percentage rate = ratePeriods.get(0).annualRate();
        for (RatePeriod period : ratePeriods) {
            if (!period.effectiveFrom().isAfter(date)) {
                rate = period.annualRate();
            }
        }
        return rate;
    }

    /**
     * Records a regular repayment. The amount is allocated to the oldest unpaid installments, interest first.
     * Calling again with the same idempotency key returns the original repayment without paying twice.
     */
    public Repayment recordRepayment(Money amount, LocalDate paidOn, String idempotencyKey) {
        Optional<Repayment> duplicate = findByIdempotencyKey(idempotencyKey);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        requireActive();
        requirePositive(amount);
        if (amount.compareTo(totalRemaining()) > 0) {
            throw new DomainException("OVERPAYMENT", "Amount exceeds the remaining balance " + totalRemaining());
        }
        Money rest = amount;
        Money principalPart = zero();
        Money interestPart = zero();
        for (Installment installment : installments) {
            if (!rest.isPositive()) {
                break;
            }
            if (installment.isPaid()) {
                continue;
            }
            Installment.Allocation allocation = installment.pay(rest);
            principalPart = principalPart.plus(allocation.principal());
            interestPart = interestPart.plus(allocation.interest());
            rest = rest.minus(allocation.total());
        }
        Repayment repayment = new Repayment(UUID.randomUUID(), RepaymentType.REGULAR, paidOn, amount, principalPart,
                interestPart, zero(), idempotencyKey);
        repayments.add(repayment);
        if (totalRemaining().isZero()) {
            status = LoanStatus.PAID_OFF;
        }
        return repayment;
    }

    /**
     * Repays {@code principalAmount} before it is due. A fee of {@code earlyRepaymentFeeRate} is charged on top, and
     * the remaining (untouched) installments are recalculated for the reduced balance over the same periods.
     */
    public Repayment prepay(Money principalAmount, LocalDate paidOn, String idempotencyKey,
                            RepaymentScheduleGenerator generator) {
        Optional<Repayment> duplicate = findByIdempotencyKey(idempotencyKey);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        requireActive();
        requirePositive(principalAmount);
        boolean hasOpenDueInstallments = installments.stream()
                .anyMatch(i -> !i.isPaid() && (!i.isUntouched() || i.getDueDate().isBefore(paidOn)));
        if (hasOpenDueInstallments) {
            throw new DomainException("SETTLE_DUE_INSTALLMENTS_FIRST",
                    "Pay overdue or partially paid installments before prepaying");
        }
        Money futureBalance = untouchedPrincipal();
        if (principalAmount.compareTo(futureBalance) > 0) {
            throw new DomainException("OVERPAYMENT", "Prepayment exceeds the outstanding principal " + futureBalance);
        }
        Money fee = principalAmount.times(earlyRepaymentFeeRate.asFraction()).rounded();
        regenerateUntouched(futureBalance.minus(principalAmount), generator);
        Repayment repayment = new Repayment(UUID.randomUUID(), RepaymentType.PREPAYMENT, paidOn,
                principalAmount.plus(fee), principalAmount, zero(), fee, idempotencyKey);
        repayments.add(repayment);
        if (totalRemaining().isZero()) {
            status = LoanStatus.PAID_OFF;
        }
        return repayment;
    }

    /** Floating rate: applies {@code newRate} from {@code effectiveFrom} and recalculates untouched installments. */
    public void changeRate(LocalDate effectiveFrom, Percentage newRate, RepaymentScheduleGenerator generator) {
        requireActive();
        if (repaymentMethod == RepaymentMethod.INTEREST_FREE) {
            throw new DomainException("INTEREST_FREE_LOAN", "Interest-free loans have no rate");
        }
        if (effectiveFrom.isBefore(startDate)) {
            throw new DomainException("INVALID_EFFECTIVE_DATE", "Rate change cannot precede the loan start date");
        }
        ratePeriods.removeIf(p -> p.effectiveFrom().equals(effectiveFrom));
        ratePeriods.add(new RatePeriod(effectiveFrom, newRate));
        ratePeriods.sort(Comparator.comparing(RatePeriod::effectiveFrom));
        regenerateUntouched(untouchedPrincipal(), generator);
    }

    private void regenerateUntouched(Money balance, RepaymentScheduleGenerator generator) {
        List<Installment> kept = installments.stream().filter(i -> !i.isUntouched()).toList();
        int periods = installments.size() - kept.size();
        LocalDate periodStart = kept.isEmpty() ? startDate : kept.get(kept.size() - 1).getDueDate();
        installments.clear();
        installments.addAll(kept);
        if (balance.isPositive() && periods > 0) {
            installments.addAll(generator.generate(balance, periods, periodStart, kept.size() + 1, this::rateAt));
        }
    }

    public void updateDetails(String counterpartyName, String note) {
        if (counterpartyName != null) {
            if (counterpartyName.isBlank()) {
                throw new DomainException("COUNTERPARTY_REQUIRED", "Counterparty name is required");
            }
            this.counterpartyName = counterpartyName.trim();
        }
        if (note != null) {
            this.note = note;
        }
    }

    public Money outstandingPrincipal() {
        return installments.stream().map(Installment::remainingPrincipal).reduce(zero(), Money::plus);
    }

    public Money totalRemaining() {
        return installments.stream().map(Installment::remaining).reduce(zero(), Money::plus);
    }

    public Money totalScheduledInterest() {
        return installments.stream().map(Installment::getInterestDue).reduce(zero(), Money::plus);
    }

    public Optional<Installment> nextInstallment() {
        return installments.stream().filter(i -> !i.isPaid()).findFirst();
    }

    public List<Installment> overdueInstallments(LocalDate asOf) {
        return installments.stream().filter(i -> !i.isPaid() && i.getDueDate().isBefore(asOf)).toList();
    }

    public Percentage currentRate(LocalDate asOf) {
        return rateAt(asOf);
    }

    private Money untouchedPrincipal() {
        return installments.stream().filter(Installment::isUntouched).map(Installment::remainingPrincipal)
                .reduce(zero(), Money::plus);
    }

    private Optional<Repayment> findByIdempotencyKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return repayments.stream().filter(r -> key.equals(r.idempotencyKey())).findFirst();
    }

    private void requireActive() {
        if (status != LoanStatus.ACTIVE) {
            throw new DomainException("LOAN_NOT_ACTIVE", "Loan is already paid off");
        }
    }

    private static void requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new DomainException("INVALID_AMOUNT", "Amount must be greater than zero");
        }
    }

    private Money zero() {
        return Money.zero(principal.currency());
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getCounterpartyName() { return counterpartyName; }
    public LoanDirection getDirection() { return direction; }
    public LoanType getType() { return type; }
    public Money getPrincipal() { return principal; }
    public int getTermMonths() { return termMonths; }
    public LocalDate getStartDate() { return startDate; }
    public RepaymentMethod getRepaymentMethod() { return repaymentMethod; }
    public List<RatePeriod> getRatePeriods() { return Collections.unmodifiableList(ratePeriods); }
    public Percentage getEarlyRepaymentFeeRate() { return earlyRepaymentFeeRate; }
    public LoanStatus getStatus() { return status; }
    public String getNote() { return note; }
    public List<Installment> getInstallments() { return Collections.unmodifiableList(installments); }
    public List<Repayment> getRepayments() { return Collections.unmodifiableList(repayments); }
    public Long getVersion() { return version; }
}
