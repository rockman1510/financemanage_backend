package com.financemanage.saving.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.saving.api.SavingEvents.SavingGoalMilestone;
import com.financemanage.saving.domain.SavingGoalRepository;
import com.financemanage.saving.domain.model.ContributionFrequency;
import com.financemanage.saving.domain.model.MovementSource;
import com.financemanage.saving.domain.model.SavingGoal;
import com.financemanage.saving.domain.model.SavingMovement;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SavingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 4, 1);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();
    private static final UUID USD_WALLET = UUID.randomUUID();

    private final List<Object> events = new ArrayList<>();
    private final InMemoryGoals repository = new InMemoryGoals();
    private final SavingService service = new SavingService(repository, new FakeLedger(), events::add,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void depositAsksTheLedgerToTakeTheMoneyAndItsAnswerAnnouncesMilestones() {
        SavingGoal goal = service.create(OWNER, "Du lịch", new BigDecimal("10000000"), null, null, null);

        SavingMovement movement = service.deposit(goal.getId(), OWNER, new BigDecimal("6000000"), CASH, null);

        assertThat(events).singleElement().isInstanceOfSatisfying(RecordExternalTransaction.class, c -> {
            assertThat(c.source()).isEqualTo("SAVING");
            assertThat(c.inflow()).isFalse();
            assertThat(c.categoryId()).isNull();
            assertThat(c.amount()).isEqualByComparingTo("6000000");
        });
        service.movementRecorded(movement.getId(), UUID.randomUUID());
        assertThat(events).anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(SavingGoalMilestone.class,
                m -> assertThat(m.percent()).isEqualTo(50)));
    }

    @Test
    void aRejectedDepositIsUndoneAndWalletsMustMatch() {
        SavingGoal goal = service.create(OWNER, "Du lịch", new BigDecimal("10000000"), null, null, null);
        SavingMovement movement = service.deposit(goal.getId(), OWNER, new BigDecimal("1000000"), CASH, null);

        service.movementRejected(movement.getId(), "WALLET_ARCHIVED: archived");

        assertThat(service.get(goal.getId(), OWNER).getSaved()).isEqualTo(Money.of(0, "VND"));
        assertThatThrownBy(() -> service.deposit(goal.getId(), OWNER, BigDecimal.TEN, USD_WALLET, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
        service.delete(goal.getId(), OWNER); // empty again, the failed movement is not pending
        assertThat(service.list(OWNER)).isEmpty();
    }

    @Test
    void roundUpsSaveTheDifferenceOfExpenses() {
        SavingGoal goal = service.create(OWNER, "Du lịch", new BigDecimal("10000000"), null, null, null);
        service.setRoundUp(goal.getId(), OWNER, CASH, new BigDecimal("10000"));
        SavingGoal other = service.create(OWNER, "Khác", new BigDecimal("100"), null, null, null);

        service.onExpense(new TransactionSnapshot(UUID.randomUUID(), OWNER, CASH, "EXPENSE", new BigDecimal("47000"),
                new BigDecimal("-47000"), "VND", UUID.randomUUID(), TODAY, false, null, null, null, null));

        SavingGoal after = service.get(goal.getId(), OWNER);
        assertThat(after.getSaved()).isEqualTo(Money.of(3_000, "VND"));
        assertThat(after.getMovements()).singleElement()
                .satisfies(m -> assertThat(m.getSource()).isEqualTo(MovementSource.ROUND_UP));
        assertThatThrownBy(() -> service.setRoundUp(other.getId(), OWNER, CASH, BigDecimal.TEN))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("ROUND_UP_TAKEN");
    }

    @Test
    void automaticContributionCatchesUpFromAStartInThePast() {
        SavingGoal goal = service.create(OWNER, "Du lịch", new BigDecimal("10000000"), null, null, null);

        SavingGoal after = service.setAutoContribution(goal.getId(), OWNER, new BigDecimal("500000"), CASH,
                ContributionFrequency.MONTHLY, TODAY.minusMonths(2));

        assertThat(after.getSaved()).isEqualTo(Money.of(1_500_000, "VND"));
        assertThat(after.nextAutoDate()).isEqualTo(TODAY.plusMonths(1));
        assertThat(service.goalsWithDueAutoContribution(TODAY)).isEmpty();
        assertThat(service.goalsWithDueAutoContribution(TODAY.plusMonths(1))).hasSize(1);
        assertThat(events).filteredOn(RecordExternalTransaction.class::isInstance).hasSize(3);
    }

    @Test
    void nonEmptyGoalsCannotBeDeleted() {
        SavingGoal goal = service.create(OWNER, "Du lịch", new BigDecimal("10000000"), null, null, null);
        service.deposit(goal.getId(), OWNER, new BigDecimal("1000"), CASH, null);

        assertThatThrownBy(() -> service.delete(goal.getId(), OWNER))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("GOAL_NOT_EMPTY");
        assertThat(service.savedAmounts(OWNER)).singleElement()
                .satisfies(s -> assertThat(s.saved()).isEqualTo(Money.of(1000, "VND")));
    }

    private static class FakeLedger implements LedgerQueryApi {
        @Override
        public List<WalletBalance> walletBalances(UUID ownerId) {
            return List.of(new WalletBalance(CASH, "Tiền mặt", "CASH", Money.of(0, "VND"), false, false),
                    new WalletBalance(USD_WALLET, "USD", "CASH", Money.of(0, "USD"), false, false));
        }

        @Override
        public List<CategoryView> categories(UUID ownerId) {
            return List.of();
        }

        @Override
        public List<Money> expenseTotals(UUID ownerId, Set<UUID> categoryIds, UUID walletId, LocalDate from,
                                         LocalDate to) {
            return List.of();
        }
    }

    private static class InMemoryGoals implements SavingGoalRepository {
        final Map<UUID, SavingGoal> store = new LinkedHashMap<>();

        @Override
        public SavingGoal save(SavingGoal goal) {
            store.put(goal.getId(), goal);
            return goal;
        }

        @Override
        public Optional<SavingGoal> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(g -> g.getOwnerId().equals(ownerId));
        }

        @Override
        public Optional<SavingGoal> findByMovement(UUID movementId) {
            return store.values().stream()
                    .filter(g -> g.getMovements().stream().anyMatch(m -> m.getId().equals(movementId))).findFirst();
        }

        @Override
        public List<SavingGoal> findByOwner(UUID ownerId) {
            return store.values().stream().filter(g -> g.getOwnerId().equals(ownerId)).toList();
        }

        @Override
        public List<SavingGoal> findWithAutoContribution() {
            return store.values().stream().filter(g -> g.getAutoFrequency() != null).toList();
        }

        @Override
        public List<SavingGoal> findRoundingUp(UUID ownerId, UUID walletId) {
            return store.values().stream()
                    .filter(g -> g.getOwnerId().equals(ownerId) && walletId.equals(g.getRoundUpWalletId())).toList();
        }

        @Override
        public void delete(SavingGoal goal) {
            store.remove(goal.getId());
        }

        @Override
        public void deleteByOwner(UUID ownerId) {
            store.values().removeIf(g -> g.getOwnerId().equals(ownerId));
        }
    }
}
