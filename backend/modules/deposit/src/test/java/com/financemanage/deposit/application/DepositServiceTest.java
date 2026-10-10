package com.financemanage.deposit.application;

import static com.financemanage.deposit.DepositFixtures.OWNER;
import static com.financemanage.deposit.DepositFixtures.demandDeposit;
import static com.financemanage.deposit.DepositFixtures.termDeposit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.deposit.api.DepositEvents.DepositChanged;
import com.financemanage.deposit.api.DepositEvents.DepositMatured;
import com.financemanage.deposit.api.DepositEvents.DepositMaturingSoon;
import com.financemanage.deposit.api.DepositEvents.DepositOpened;
import com.financemanage.deposit.api.DepositEvents.DepositSettled;
import com.financemanage.deposit.domain.model.DepositStatus;
import com.financemanage.deposit.domain.model.RolloverPolicy;
import com.financemanage.deposit.domain.policy.CompoundMonthlyInterestCalculator;
import com.financemanage.deposit.domain.policy.InterestCalculatorRegistry;
import com.financemanage.deposit.domain.policy.Simple30360InterestCalculator;
import com.financemanage.deposit.domain.policy.SimpleActual365InterestCalculator;
import com.financemanage.shared.domain.CashFlowItem;
import com.financemanage.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DepositServiceTest {

    private final InMemoryDepositRepository repository = new InMemoryDepositRepository();
    private final List<Object> events = new ArrayList<>();
    private DepositService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2025, 4, 1).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        var registry = new InterestCalculatorRegistry(List.of(new SimpleActual365InterestCalculator(),
                new Simple30360InterestCalculator(), new CompoundMonthlyInterestCalculator()));
        service = new DepositService(repository, registry, events::add, clock);
    }

    @Test
    void openPersistsDepositPublishesEventAndComputesInterest() {
        DepositView view = service.open(termDeposit(RolloverPolicy.NONE));

        assertThat(repository.store).containsKey(view.deposit().getId());
        assertThat(view.accruedInterest().amount()).isEqualByComparingTo("1479452"); // 90 days as of Apr 1
        assertThat(view.expectedInterestAtMaturity().amount()).isEqualByComparingTo("2975342");
        assertThat(events).singleElement().isInstanceOf(DepositOpened.class);
    }

    @Test
    void previewDoesNotPersist() {
        DepositPreview preview = service.preview(termDeposit(RolloverPolicy.NONE));

        assertThat(preview.maturityDate()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(preview.totalAtMaturity().amount()).isEqualByComparingTo("102975342");
        assertThat(repository.store).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    void otherUsersCannotAccessDeposit() {
        UUID id = service.open(termDeposit(RolloverPolicy.NONE)).deposit().getId();

        assertThatThrownBy(() -> service.get(id, UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void settleDefaultsToTodayAndPublishesEvent() {
        UUID id = service.open(termDeposit(RolloverPolicy.NONE)).deposit().getId();

        var quote = service.settle(id, OWNER, null);

        assertThat(quote.date()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(quote.early()).isTrue();
        assertThat(repository.store.get(id).getStatus()).isEqualTo(DepositStatus.SETTLED);
        assertThat(events).last().isInstanceOfSatisfying(DepositSettled.class, e -> assertThat(e.early()).isTrue());
    }

    @Test
    void processMaturitiesPublishesOneEventPerMaturedTerm() {
        service.open(termDeposit(RolloverPolicy.PRINCIPAL_ONLY));
        service.open(termDeposit(RolloverPolicy.NONE));
        events.clear();

        int processed = service.processMaturities(LocalDate.of(2026, 1, 1));

        assertThat(processed).isEqualTo(2);
        // rollover deposit matured twice (Jul 1 and Jan 1), the other once
        assertThat(events).hasSize(3).allMatch(DepositMatured.class::isInstance);
    }

    @Test
    void remindsDepositsMaturingInConfiguredDays() {
        service.open(termDeposit(RolloverPolicy.NONE));
        events.clear();

        assertThat(service.remindUpcomingMaturities(LocalDate.of(2025, 6, 24), 7)).isEqualTo(1);
        assertThat(service.remindUpcomingMaturities(LocalDate.of(2025, 6, 23), 7)).isZero();
        assertThat(events).singleElement().isInstanceOf(DepositMaturingSoon.class);
    }

    @Test
    void updateAndDeletePublishChangeEvents() {
        UUID id = service.open(termDeposit(RolloverPolicy.NONE)).deposit().getId();
        events.clear();

        service.updateDetails(id, OWNER, "ACB", null, null);
        service.delete(id, OWNER);

        assertThat(events).hasSize(2).allMatch(DepositChanged.class::isInstance);
        assertThat(repository.store).isEmpty();
    }

    @Test
    void expectedCashFlowsDependOnRolloverPolicy() {
        service.open(termDeposit(RolloverPolicy.NONE));
        service.open(termDeposit(RolloverPolicy.PRINCIPAL_ONLY));
        service.open(termDeposit(RolloverPolicy.PRINCIPAL_AND_INTEREST));
        service.open(demandDeposit());

        List<CashFlowItem> flows = service.expectedCashFlows(OWNER, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 12, 31));

        assertThat(flows).extracting(f -> f.amount().amount().toBigInteger().longValue())
                .containsExactlyInAnyOrder(102_975_342L, 2_975_342L);
        assertThat(flows).allMatch(f -> f.direction() == CashFlowItem.Direction.IN);
    }

    @Test
    void activePositionsExcludeSettledDeposits() {
        UUID settled = service.open(termDeposit(RolloverPolicy.NONE)).deposit().getId();
        service.open(demandDeposit());
        service.settle(settled, OWNER, LocalDate.of(2025, 2, 1));

        assertThat(service.activePositions(OWNER, LocalDate.of(2025, 4, 1))).singleElement()
                .satisfies(p -> assertThat(p.institutionName()).isEqualTo("Techcombank"));
    }
}
