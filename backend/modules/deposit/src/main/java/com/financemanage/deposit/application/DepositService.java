package com.financemanage.deposit.application;

import com.financemanage.deposit.api.DepositEvents.DepositChanged;
import com.financemanage.deposit.api.DepositEvents.DepositMatured;
import com.financemanage.deposit.api.DepositEvents.DepositMaturingSoon;
import com.financemanage.deposit.api.DepositEvents.DepositOpened;
import com.financemanage.deposit.api.DepositEvents.DepositSettled;
import com.financemanage.deposit.api.DepositPosition;
import com.financemanage.deposit.api.DepositQueryApi;
import com.financemanage.deposit.domain.DepositRepository;
import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.deposit.domain.model.DepositStatus;
import com.financemanage.deposit.domain.model.DepositType;
import com.financemanage.deposit.domain.model.InterestPaymentMethod;
import com.financemanage.deposit.domain.model.MaturityOutcome;
import com.financemanage.deposit.domain.model.NewDeposit;
import com.financemanage.deposit.domain.model.RolloverPolicy;
import com.financemanage.deposit.domain.model.SettlementQuote;
import com.financemanage.deposit.domain.policy.InterestCalculator;
import com.financemanage.deposit.domain.policy.InterestCalculatorRegistry;
import com.financemanage.shared.domain.CashFlowItem;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deposit use cases. Also serves the module's public {@link DepositQueryApi}. */
@Service
@Transactional
public class DepositService implements DepositQueryApi {

    private final DepositRepository repository;
    private final InterestCalculatorRegistry calculators;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DepositService(DepositRepository repository, InterestCalculatorRegistry calculators,
                          ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.calculators = calculators;
        this.events = events;
        this.clock = clock;
    }

    public DepositView open(NewDeposit spec) {
        Deposit deposit = repository.save(Deposit.open(spec));
        events.publishEvent(new DepositOpened(deposit.getId(), deposit.getOwnerId(),
                deposit.getPrincipal().amount(), deposit.getPrincipal().currencyCode()));
        return view(deposit);
    }

    @Transactional(readOnly = true)
    public DepositPreview preview(NewDeposit spec) {
        Deposit draft = Deposit.open(spec);
        InterestCalculator calculator = calculatorFor(draft);
        Money interest = draft.expectedInterestAtMaturity(calculator)
                .orElseGet(() -> calculator.interest(draft.getPrincipal(), draft.getAnnualRate(),
                        draft.getOpenDate(), draft.getOpenDate().plusYears(1)).rounded());
        return new DepositPreview(draft.getMaturityDate(), interest, draft.getPrincipal().plus(interest));
    }

    @Transactional(readOnly = true)
    public DepositView get(UUID id, UUID ownerId) {
        return view(load(id, ownerId));
    }

    @Transactional(readOnly = true)
    public List<DepositView> list(UUID ownerId, DepositStatus status) {
        return repository.findByOwner(ownerId, status).stream().map(this::view).toList();
    }

    public DepositView updateDetails(UUID id, UUID ownerId, String institutionName, String accountNumber,
                                     String note) {
        Deposit deposit = load(id, ownerId);
        deposit.updateDetails(institutionName, accountNumber, note);
        Deposit saved = repository.save(deposit);
        events.publishEvent(new DepositChanged(id, ownerId));
        return view(saved);
    }

    public void delete(UUID id, UUID ownerId) {
        repository.delete(load(id, ownerId));
        events.publishEvent(new DepositChanged(id, ownerId));
    }

    @Transactional(readOnly = true)
    public SettlementQuote settlementQuote(UUID id, UUID ownerId, LocalDate date) {
        Deposit deposit = load(id, ownerId);
        return deposit.settlementQuote(calculatorFor(deposit), dateOrToday(date));
    }

    public SettlementQuote settle(UUID id, UUID ownerId, LocalDate date) {
        Deposit deposit = load(id, ownerId);
        SettlementQuote quote = deposit.settle(calculatorFor(deposit), dateOrToday(date));
        repository.save(deposit);
        events.publishEvent(new DepositSettled(id, ownerId, quote.total().amount(), quote.total().currencyCode(),
                quote.early()));
        return quote;
    }

    /** Matures (and rolls over) every deposit whose term ended on or before {@code asOf}. */
    public int processMaturities(LocalDate asOf) {
        int processed = 0;
        for (Deposit deposit : repository.findActiveMaturingOnOrBefore(asOf)) {
            List<MaturityOutcome> outcomes = deposit.processMaturity(calculatorFor(deposit), asOf);
            repository.save(deposit);
            for (MaturityOutcome outcome : outcomes) {
                events.publishEvent(new DepositMatured(deposit.getId(), deposit.getOwnerId(),
                        deposit.getInstitutionName(), outcome.maturityDate(), outcome.rolledOver(),
                        outcome.newPrincipal().amount(), outcome.newPrincipal().currencyCode()));
            }
            processed++;
        }
        return processed;
    }

    /** Publishes a reminder for deposits maturing exactly {@code daysBefore} days after {@code asOf}. */
    @Transactional(readOnly = true)
    public int remindUpcomingMaturities(LocalDate asOf, int daysBefore) {
        List<Deposit> deposits = repository.findActiveMaturingOn(asOf.plusDays(daysBefore));
        deposits.forEach(d -> events.publishEvent(new DepositMaturingSoon(d.getId(), d.getOwnerId(),
                d.getInstitutionName(), d.getMaturityDate(), daysBefore)));
        return deposits.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepositPosition> activePositions(UUID ownerId, LocalDate asOf) {
        List<DepositPosition> positions = new ArrayList<>();
        for (Deposit d : repository.findByOwner(ownerId, null)) {
            if (d.getStatus() == DepositStatus.SETTLED) {
                continue;
            }
            positions.add(new DepositPosition(d.getId(), d.getInstitutionName(), d.getPrincipal(),
                    d.accruedInterest(calculatorFor(d), asOf), d.getMaturityDate()));
        }
        return positions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashFlowItem> expectedCashFlows(UUID ownerId, LocalDate from, LocalDate to) {
        List<CashFlowItem> items = new ArrayList<>();
        for (Deposit d : repository.findByOwner(ownerId, DepositStatus.ACTIVE)) {
            if (d.getType() != DepositType.TERM || d.getMaturityDate().isBefore(from)
                    || d.getMaturityDate().isAfter(to)) {
                continue;
            }
            Money interest = d.expectedInterestAtMaturity(calculatorFor(d)).orElseThrow();
            Money cash = switch (d.getRolloverPolicy()) {
                case NONE -> d.getPrincipal().plus(interest);
                case PRINCIPAL_ONLY -> interest;
                case PRINCIPAL_AND_INTEREST ->
                        d.getInterestPaymentMethod() == InterestPaymentMethod.AT_MATURITY ? null : interest;
            };
            if (cash != null && cash.isPositive()) {
                String title = d.getRolloverPolicy() == RolloverPolicy.NONE
                        ? "Đáo hạn " + d.getInstitutionName()
                        : "Lãi đáo hạn " + d.getInstitutionName();
                items.add(new CashFlowItem(d.getMaturityDate(), cash, CashFlowItem.Direction.IN, "DEPOSIT",
                        d.getId(), title));
            }
        }
        items.sort(Comparator.comparing(CashFlowItem::date));
        return items;
    }

    private DepositView view(Deposit deposit) {
        InterestCalculator calculator = calculatorFor(deposit);
        return new DepositView(deposit, deposit.accruedInterest(calculator, LocalDate.now(clock)),
                deposit.expectedInterestAtMaturity(calculator).orElse(null));
    }

    private Deposit load(UUID id, UUID ownerId) {
        return repository.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Deposit", id));
    }

    private InterestCalculator calculatorFor(Deposit deposit) {
        return calculators.get(deposit.getCalculationMethod());
    }

    private LocalDate dateOrToday(LocalDate date) {
        return date != null ? date : LocalDate.now(clock);
    }
}
