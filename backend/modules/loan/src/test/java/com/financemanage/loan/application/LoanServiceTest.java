package com.financemanage.loan.application;

import static com.financemanage.loan.LoanFixtures.OWNER;
import static com.financemanage.loan.LoanFixtures.loan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.loan.api.LoanEvents.InstallmentDueSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentOverdue;
import com.financemanage.loan.api.LoanEvents.LoanChanged;
import com.financemanage.loan.api.LoanEvents.LoanCreated;
import com.financemanage.loan.api.LoanEvents.LoanPaidOff;
import com.financemanage.loan.api.LoanEvents.RepaymentRecorded;
import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.loan.domain.policy.AnnuityScheduleGenerator;
import com.financemanage.loan.domain.policy.BulletScheduleGenerator;
import com.financemanage.loan.domain.policy.EqualPrincipalScheduleGenerator;
import com.financemanage.loan.domain.policy.InterestFreeScheduleGenerator;
import com.financemanage.loan.domain.policy.ScheduleGeneratorRegistry;
import com.financemanage.shared.domain.CashFlowItem;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import com.financemanage.shared.domain.Percentage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanServiceTest {

    private final InMemoryLoanRepository repository = new InMemoryLoanRepository();
    private final List<Object> events = new ArrayList<>();
    private LoanService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2025, 2, 15).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        var registry = new ScheduleGeneratorRegistry(List.of(new EqualPrincipalScheduleGenerator(),
                new AnnuityScheduleGenerator(), new BulletScheduleGenerator(), new InterestFreeScheduleGenerator()));
        service = new LoanService(repository, registry, events::add, clock);
    }

    private Loan create(LoanDirection direction) {
        return service.create(loan(RepaymentMethod.EQUAL_PRINCIPAL, 12_000_000, "12", 3, direction));
    }

    @Test
    void createPersistsAndPublishes() {
        Loan loan = create(LoanDirection.BORROW);

        assertThat(repository.store).containsKey(loan.getId());
        assertThat(events).singleElement().isInstanceOf(LoanCreated.class);
    }

    @Test
    void previewDoesNotPersist() {
        SchedulePreview preview = service.previewSchedule(loan(RepaymentMethod.BULLET, 12_000_000, "12", 3));

        assertThat(preview.totalInterest()).isEqualTo(Money.of(360_000, "VND"));
        assertThat(preview.totalPayment()).isEqualTo(Money.of(12_360_000, "VND"));
        assertThat(repository.store).isEmpty();
    }

    @Test
    void repaymentDefaultsToTodayAndPublishesEventsUntilPaidOff() {
        Loan loan = create(LoanDirection.BORROW);
        events.clear();

        var repayment = service.recordRepayment(loan.getId(), OWNER, Money.of(4_120_000, "VND"), null, null);
        service.recordRepayment(loan.getId(), OWNER, repository.store.get(loan.getId()).totalRemaining(), null, null);

        assertThat(repayment.paidOn()).isEqualTo(LocalDate.of(2025, 2, 15));
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(RepaymentRecorded.class);
        assertThat(events.get(2)).isInstanceOf(LoanPaidOff.class);
    }

    @Test
    void idempotentReplayPublishesNothing() {
        Loan loan = create(LoanDirection.BORROW);
        service.recordRepayment(loan.getId(), OWNER, Money.of(1_000, "VND"), null, "abc");
        events.clear();

        service.recordRepayment(loan.getId(), OWNER, Money.of(1_000, "VND"), null, "abc");

        assertThat(events).isEmpty();
        assertThat(repository.store.get(loan.getId()).getRepayments()).hasSize(1);
    }

    @Test
    void changeRatePublishesChange() {
        Loan loan = create(LoanDirection.BORROW);
        events.clear();

        Loan updated = service.changeRate(loan.getId(), OWNER, LocalDate.of(2025, 2, 15), Percentage.of("24"));

        assertThat(updated.getRatePeriods()).hasSize(2);
        assertThat(events).singleElement().isInstanceOf(LoanChanged.class);
    }

    @Test
    void unknownLoanIsNotFound() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), OWNER)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void remindersAndOverdueAlerts() {
        create(LoanDirection.BORROW); // installments due Feb 15, Mar 15, Apr 15
        events.clear();

        assertThat(service.remindUpcomingInstallments(LocalDate.of(2025, 3, 12), 3)).isEqualTo(1);
        assertThat(service.flagNewlyOverdueInstallments(LocalDate.of(2025, 2, 16))).isEqualTo(1);
        assertThat(service.flagNewlyOverdueInstallments(LocalDate.of(2025, 2, 17))).isZero();
        assertThat(events.get(0)).isInstanceOf(InstallmentDueSoon.class);
        assertThat(events.get(1)).isInstanceOf(InstallmentOverdue.class);
    }

    @Test
    void cashFlowsAreOutgoingForBorrowingAndIncomingForLending() {
        create(LoanDirection.BORROW);
        create(LoanDirection.LEND);

        List<CashFlowItem> flows = service.expectedCashFlows(OWNER, LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 3, 31));

        assertThat(flows).hasSize(2);
        assertThat(flows).extracting(CashFlowItem::direction)
                .containsExactlyInAnyOrder(CashFlowItem.Direction.OUT, CashFlowItem.Direction.IN);
        assertThat(flows).allSatisfy(f -> assertThat(f.amount()).isEqualTo(Money.of(4_080_000, "VND")));
    }

    @Test
    void activePositionsReportOutstandingAndOverdue() {
        create(LoanDirection.BORROW);

        assertThat(service.activePositions(OWNER, LocalDate.of(2025, 3, 20))).singleElement().satisfies(p -> {
            assertThat(p.borrowed()).isTrue();
            assertThat(p.outstandingPrincipal()).isEqualTo(Money.of(12_000_000, "VND"));
            assertThat(p.overdueInstallments()).isEqualTo(2);
        });
    }
}
