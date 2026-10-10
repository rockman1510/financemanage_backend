package com.financemanage.deposit.domain.model;

import com.financemanage.deposit.domain.policy.InterestCalculator;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Deposit aggregate (sổ tiết kiệm). Pure Java – no framework dependency – so business rules are easy to test
 * and the module can be extracted into its own service without touching this class.
 */
public class Deposit {

    private static final int MAX_TERM_MONTHS = 600;

    private final UUID id;
    private final UUID ownerId;
    private String institutionName;
    private String accountNumber;
    private final DepositType type;
    private Money principal;
    private final Percentage annualRate;
    private final Percentage earlyWithdrawalRate;
    private final Integer termMonths;
    private LocalDate openDate;
    private LocalDate maturityDate;
    private final InterestPaymentMethod interestPaymentMethod;
    private final RolloverPolicy rolloverPolicy;
    private final InterestCalculationMethod calculationMethod;
    private DepositStatus status;
    private String note;
    private LocalDate settledOn;
    private Money settledAmount;
    private final List<DepositTransaction> transactions;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Deposit(UUID id, UUID ownerId, String institutionName, String accountNumber, DepositType type,
                   Money principal, Percentage annualRate, Percentage earlyWithdrawalRate, Integer termMonths,
                   LocalDate openDate, LocalDate maturityDate, InterestPaymentMethod interestPaymentMethod,
                   RolloverPolicy rolloverPolicy, InterestCalculationMethod calculationMethod, DepositStatus status,
                   String note, LocalDate settledOn, Money settledAmount, List<DepositTransaction> transactions,
                   Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.institutionName = institutionName;
        this.accountNumber = accountNumber;
        this.type = Objects.requireNonNull(type);
        this.principal = Objects.requireNonNull(principal);
        this.annualRate = Objects.requireNonNull(annualRate);
        this.earlyWithdrawalRate = earlyWithdrawalRate == null ? Percentage.zero() : earlyWithdrawalRate;
        this.termMonths = termMonths;
        this.openDate = Objects.requireNonNull(openDate);
        this.maturityDate = maturityDate;
        this.interestPaymentMethod = Objects.requireNonNull(interestPaymentMethod);
        this.rolloverPolicy = Objects.requireNonNull(rolloverPolicy);
        this.calculationMethod = Objects.requireNonNull(calculationMethod);
        this.status = Objects.requireNonNull(status);
        this.note = note;
        this.settledOn = settledOn;
        this.settledAmount = settledAmount;
        this.transactions = new ArrayList<>(transactions == null ? List.of() : transactions);
        this.version = version;
    }

    public static Deposit open(NewDeposit spec) {
        if (spec.institutionName() == null || spec.institutionName().isBlank()) {
            throw new DomainException("INSTITUTION_REQUIRED", "Institution name is required");
        }
        if (spec.principal() == null || !spec.principal().isPositive()) {
            throw new DomainException("INVALID_PRINCIPAL", "Principal must be greater than zero");
        }
        if (spec.openDate() == null) {
            throw new DomainException("OPEN_DATE_REQUIRED", "Open date is required");
        }
        Integer term = null;
        LocalDate maturity = null;
        RolloverPolicy rollover = RolloverPolicy.NONE;
        if (spec.type() == DepositType.TERM) {
            if (spec.termMonths() == null || spec.termMonths() < 1 || spec.termMonths() > MAX_TERM_MONTHS) {
                throw new DomainException("INVALID_TERM", "Term deposits need a term between 1 and 600 months");
            }
            term = spec.termMonths();
            maturity = spec.openDate().plusMonths(term);
            rollover = spec.rolloverPolicy() == null ? RolloverPolicy.NONE : spec.rolloverPolicy();
        }
        Deposit deposit = new Deposit(UUID.randomUUID(), spec.ownerId(), spec.institutionName().trim(),
                spec.accountNumber(), spec.type(), spec.principal(), spec.annualRate(), spec.earlyWithdrawalRate(),
                term, spec.openDate(), maturity,
                spec.interestPaymentMethod() == null ? InterestPaymentMethod.AT_MATURITY : spec.interestPaymentMethod(),
                rollover,
                spec.calculationMethod() == null ? InterestCalculationMethod.SIMPLE_ACTUAL_365 : spec.calculationMethod(),
                DepositStatus.ACTIVE, spec.note(), null, null, List.of(), null);
        deposit.transactions.add(DepositTransaction.of(DepositTransactionType.OPEN, spec.principal(), spec.openDate()));
        return deposit;
    }

    /** Interest earned from the open date up to {@code asOf} (capped at maturity for term deposits). */
    public Money accruedInterest(InterestCalculator calculator, LocalDate asOf) {
        if (status == DepositStatus.SETTLED) {
            return Money.zero(principal.currency());
        }
        LocalDate end = maturityDate != null && asOf.isAfter(maturityDate) ? maturityDate : asOf;
        return calculator.interest(principal, annualRate, openDate, end).rounded();
    }

    /** Interest at maturity for the current term; empty for demand deposits. */
    public Optional<Money> expectedInterestAtMaturity(InterestCalculator calculator) {
        if (type != DepositType.TERM) {
            return Optional.empty();
        }
        return Optional.of(calculator.interest(principal, annualRate, openDate, maturityDate).rounded());
    }

    public SettlementQuote settlementQuote(InterestCalculator calculator, LocalDate date) {
        requireNotSettled();
        if (date.isBefore(openDate)) {
            throw new DomainException("INVALID_SETTLEMENT_DATE", "Settlement date is before the open date");
        }
        Money fullInterest = accruedInterest(calculator, date);
        boolean early = type == DepositType.TERM && date.isBefore(maturityDate);
        Money interest = early
                ? calculator.interest(principal, earlyWithdrawalRate, openDate, date).rounded()
                : fullInterest;
        Money lost = early ? fullInterest.minus(interest) : Money.zero(principal.currency());
        return new SettlementQuote(date, principal, interest, principal.plus(interest), early, lost);
    }

    public SettlementQuote settle(InterestCalculator calculator, LocalDate date) {
        SettlementQuote quote = settlementQuote(calculator, date);
        status = DepositStatus.SETTLED;
        settledOn = date;
        settledAmount = quote.total();
        transactions.add(DepositTransaction.of(DepositTransactionType.SETTLE, quote.total(), date));
        return quote;
    }

    public boolean isDueForMaturity(LocalDate asOf) {
        return type == DepositType.TERM && status == DepositStatus.ACTIVE && !asOf.isBefore(maturityDate);
    }

    /**
     * Applies every maturity that happened up to {@code asOf}. A deposit that rolls over and whose new term also
     * ended (e.g. the job did not run for a long time) is matured again.
     */
    public List<MaturityOutcome> processMaturity(InterestCalculator calculator, LocalDate asOf) {
        List<MaturityOutcome> outcomes = new ArrayList<>();
        while (isDueForMaturity(asOf)) {
            outcomes.add(matureCurrentTerm(calculator));
        }
        return outcomes;
    }

    private MaturityOutcome matureCurrentTerm(InterestCalculator calculator) {
        LocalDate maturedOn = maturityDate;
        Money interest = calculator.interest(principal, annualRate, openDate, maturityDate).rounded();
        if (rolloverPolicy == RolloverPolicy.NONE) {
            status = DepositStatus.MATURED;
            return new MaturityOutcome(maturedOn, interest, false, principal);
        }
        boolean capitalize = rolloverPolicy == RolloverPolicy.PRINCIPAL_AND_INTEREST
                && interestPaymentMethod == InterestPaymentMethod.AT_MATURITY;
        if (capitalize) {
            principal = principal.plus(interest);
        } else {
            transactions.add(DepositTransaction.of(DepositTransactionType.INTEREST_PAID, interest, maturedOn));
        }
        transactions.add(DepositTransaction.of(DepositTransactionType.ROLLOVER, principal, maturedOn));
        openDate = maturedOn;
        maturityDate = maturedOn.plusMonths(termMonths);
        return new MaturityOutcome(maturedOn, interest, true, principal);
    }

    public long daysUntilMaturity(LocalDate asOf) {
        if (maturityDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(asOf, maturityDate);
    }

    public void updateDetails(String institutionName, String accountNumber, String note) {
        if (institutionName != null) {
            if (institutionName.isBlank()) {
                throw new DomainException("INSTITUTION_REQUIRED", "Institution name is required");
            }
            this.institutionName = institutionName.trim();
        }
        if (accountNumber != null) {
            this.accountNumber = accountNumber;
        }
        if (note != null) {
            this.note = note;
        }
    }

    private void requireNotSettled() {
        if (status == DepositStatus.SETTLED) {
            throw new DomainException("DEPOSIT_ALREADY_SETTLED", "Deposit has already been settled");
        }
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getInstitutionName() { return institutionName; }
    public String getAccountNumber() { return accountNumber; }
    public DepositType getType() { return type; }
    public Money getPrincipal() { return principal; }
    public Percentage getAnnualRate() { return annualRate; }
    public Percentage getEarlyWithdrawalRate() { return earlyWithdrawalRate; }
    public Integer getTermMonths() { return termMonths; }
    public LocalDate getOpenDate() { return openDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public InterestPaymentMethod getInterestPaymentMethod() { return interestPaymentMethod; }
    public RolloverPolicy getRolloverPolicy() { return rolloverPolicy; }
    public InterestCalculationMethod getCalculationMethod() { return calculationMethod; }
    public DepositStatus getStatus() { return status; }
    public String getNote() { return note; }
    public LocalDate getSettledOn() { return settledOn; }
    public Money getSettledAmount() { return settledAmount; }
    public List<DepositTransaction> getTransactions() { return Collections.unmodifiableList(transactions); }
    public Long getVersion() { return version; }
}
