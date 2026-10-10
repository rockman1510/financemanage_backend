package com.financemanage.loan.application;

import com.financemanage.loan.api.LoanEvents.InstallmentDueSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentOverdue;
import com.financemanage.loan.api.LoanEvents.LoanChanged;
import com.financemanage.loan.api.LoanEvents.LoanCreated;
import com.financemanage.loan.api.LoanEvents.LoanPaidOff;
import com.financemanage.loan.api.LoanEvents.RepaymentRecorded;
import com.financemanage.loan.api.LoanPosition;
import com.financemanage.loan.api.LoanQueryApi;
import com.financemanage.loan.domain.LoanRepository;
import com.financemanage.loan.domain.model.Installment;
import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import com.financemanage.loan.domain.model.NewLoan;
import com.financemanage.loan.domain.model.Repayment;
import com.financemanage.loan.domain.policy.RepaymentScheduleGenerator;
import com.financemanage.loan.domain.policy.ScheduleGeneratorRegistry;
import com.financemanage.shared.domain.CashFlowItem;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import com.financemanage.shared.domain.Percentage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loan use cases. Also serves the module's public {@link LoanQueryApi}. */
@Service
@Transactional
public class LoanService implements LoanQueryApi {

    private final LoanRepository repository;
    private final ScheduleGeneratorRegistry generators;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LoanService(LoanRepository repository, ScheduleGeneratorRegistry generators,
                       ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.generators = generators;
        this.events = events;
        this.clock = clock;
    }

    public Loan create(NewLoan spec) {
        Loan loan = repository.save(Loan.create(spec, generators.get(spec.repaymentMethod())));
        events.publishEvent(new LoanCreated(loan.getId(), loan.getOwnerId(), loan.getPrincipal().amount(),
                loan.getPrincipal().currencyCode()));
        return loan;
    }

    @Transactional(readOnly = true)
    public SchedulePreview previewSchedule(NewLoan spec) {
        Loan draft = Loan.create(spec, generators.get(spec.repaymentMethod()));
        Money interest = draft.totalScheduledInterest();
        return new SchedulePreview(draft.getInstallments(), interest, draft.getPrincipal().plus(interest));
    }

    @Transactional(readOnly = true)
    public Loan get(UUID id, UUID ownerId) {
        return load(id, ownerId);
    }

    @Transactional(readOnly = true)
    public List<Loan> list(UUID ownerId, LoanDirection direction, LoanStatus status) {
        return repository.findByOwner(ownerId, direction, status);
    }

    public Loan updateDetails(UUID id, UUID ownerId, String counterpartyName, String note) {
        Loan loan = load(id, ownerId);
        loan.updateDetails(counterpartyName, note);
        Loan saved = repository.save(loan);
        events.publishEvent(new LoanChanged(id, ownerId));
        return saved;
    }

    public void delete(UUID id, UUID ownerId) {
        repository.delete(load(id, ownerId));
        events.publishEvent(new LoanChanged(id, ownerId));
    }

    public Repayment recordRepayment(UUID id, UUID ownerId, Money amount, LocalDate paidOn, String idempotencyKey) {
        Loan loan = load(id, ownerId);
        int before = loan.getRepayments().size();
        Repayment repayment = loan.recordRepayment(amount, dateOrToday(paidOn), idempotencyKey);
        afterPayment(loan, before);
        return repayment;
    }

    public Repayment prepay(UUID id, UUID ownerId, Money principal, LocalDate paidOn, String idempotencyKey) {
        Loan loan = load(id, ownerId);
        int before = loan.getRepayments().size();
        Repayment repayment = loan.prepay(principal, dateOrToday(paidOn), idempotencyKey, generatorFor(loan));
        afterPayment(loan, before);
        return repayment;
    }

    public Loan changeRate(UUID id, UUID ownerId, LocalDate effectiveFrom, Percentage newRate) {
        Loan loan = load(id, ownerId);
        loan.changeRate(effectiveFrom, newRate, generatorFor(loan));
        Loan saved = repository.save(loan);
        events.publishEvent(new LoanChanged(id, ownerId));
        return saved;
    }

    /** Reminds owners about installments due exactly {@code daysBefore} days after {@code asOf}. */
    @Transactional(readOnly = true)
    public int remindUpcomingInstallments(LocalDate asOf, int daysBefore) {
        return publishForInstallmentsDueOn(asOf.plusDays(daysBefore), (loan, i) -> events.publishEvent(
                new InstallmentDueSoon(loan.getId(), loan.getOwnerId(), loan.getCounterpartyName(),
                        loan.getDirection() == LoanDirection.BORROW, i.getDueDate(), i.remaining().amount(),
                        i.remaining().currencyCode())));
    }

    /** Flags installments that became overdue on {@code asOf} (due the day before and still unpaid). */
    @Transactional(readOnly = true)
    public int flagNewlyOverdueInstallments(LocalDate asOf) {
        return publishForInstallmentsDueOn(asOf.minusDays(1), (loan, i) -> events.publishEvent(
                new InstallmentOverdue(loan.getId(), loan.getOwnerId(), loan.getCounterpartyName(),
                        loan.getDirection() == LoanDirection.BORROW, i.getDueDate(), i.remaining().amount(),
                        i.remaining().currencyCode())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoanPosition> activePositions(UUID ownerId, LocalDate asOf) {
        return repository.findByOwner(ownerId, null, LoanStatus.ACTIVE).stream()
                .map(loan -> new LoanPosition(loan.getId(), loan.getCounterpartyName(),
                        loan.getDirection() == LoanDirection.BORROW, loan.outstandingPrincipal(),
                        loan.nextInstallment().map(Installment::getDueDate).orElse(null),
                        loan.overdueInstallments(asOf).size()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashFlowItem> expectedCashFlows(UUID ownerId, LocalDate from, LocalDate to) {
        List<CashFlowItem> items = new ArrayList<>();
        for (Loan loan : repository.findByOwner(ownerId, null, LoanStatus.ACTIVE)) {
            boolean borrowed = loan.getDirection() == LoanDirection.BORROW;
            for (Installment i : loan.getInstallments()) {
                if (i.isPaid() || i.getDueDate().isBefore(from) || i.getDueDate().isAfter(to)) {
                    continue;
                }
                items.add(new CashFlowItem(i.getDueDate(), i.remaining(),
                        borrowed ? CashFlowItem.Direction.OUT : CashFlowItem.Direction.IN, "LOAN", loan.getId(),
                        (borrowed ? "Trả nợ " : "Thu nợ ") + loan.getCounterpartyName() + " – kỳ " + i.getPeriodNo()));
            }
        }
        items.sort(Comparator.comparing(CashFlowItem::date));
        return items;
    }

    private void afterPayment(Loan loan, int repaymentsBefore) {
        if (loan.getRepayments().size() == repaymentsBefore) {
            return; // idempotent replay – nothing changed
        }
        repository.save(loan);
        Repayment repayment = loan.getRepayments().get(loan.getRepayments().size() - 1);
        events.publishEvent(new RepaymentRecorded(loan.getId(), loan.getOwnerId(), repayment.amount().amount(),
                loan.outstandingPrincipal().amount(), repayment.amount().currencyCode()));
        if (loan.getStatus() == LoanStatus.PAID_OFF) {
            events.publishEvent(new LoanPaidOff(loan.getId(), loan.getOwnerId(), loan.getCounterpartyName()));
        }
    }

    private int publishForInstallmentsDueOn(LocalDate dueDate, BiConsumer<Loan, Installment> publisher) {
        int count = 0;
        for (Loan loan : repository.findActiveWithUnpaidInstallmentDueOn(dueDate)) {
            for (Installment i : loan.getInstallments()) {
                if (!i.isPaid() && i.getDueDate().equals(dueDate)) {
                    publisher.accept(loan, i);
                    count++;
                }
            }
        }
        return count;
    }

    private Loan load(UUID id, UUID ownerId) {
        return repository.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Loan", id));
    }

    private RepaymentScheduleGenerator generatorFor(Loan loan) {
        return generators.get(loan.getRepaymentMethod());
    }

    private LocalDate dateOrToday(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }
}
