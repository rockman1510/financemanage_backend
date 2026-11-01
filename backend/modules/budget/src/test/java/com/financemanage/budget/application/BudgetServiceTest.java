package com.financemanage.budget.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.budget.api.BudgetEvents.BudgetThresholdReached;
import com.financemanage.budget.domain.BudgetRepository;
import com.financemanage.budget.domain.model.Budget;
import com.financemanage.budget.domain.model.BudgetPeriod;
import com.financemanage.budget.domain.model.BudgetState;
import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
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

class BudgetServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 4, 21);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID COFFEE = UUID.randomUUID();
    private static final UUID SALARY = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();

    private final List<Money> spending = new ArrayList<>();
    private final List<Object> events = new ArrayList<>();
    private final InMemoryBudgets repository = new InMemoryBudgets();
    private final BudgetService service = new BudgetService(repository, new FakeLedger(), new FakeRates(),
            events::add, Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC), 80);

    @Test
    void createsAMonthlyBudgetStartingOnTheFirstAndConvertsOtherCurrencies() {
        spending.add(Money.of(3_000_000, "VND"));
        spending.add(Money.of(20, "USD"));
        spending.add(Money.of(5, "EUR"));

        BudgetView view = service.create(OWNER, new BudgetInput("Ăn uống", new BigDecimal("4000000"), null,
                Set.of(FOOD), CASH, BudgetPeriod.MONTHLY, null, null, null));

        assertThat(view.budget().getStartDate()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(view.status().spent()).isEqualTo(Money.of(3_500_000, "VND"));
        assertThat(view.status().state()).isEqualTo(BudgetState.WARNING);
        assertThat(view.unconvertedCurrencies()).containsExactly("EUR");
    }

    @Test
    void onlyExpenseCategoriesAndOwnWalletsAreAccepted() {
        assertThatThrownBy(() -> service.create(OWNER, new BudgetInput("Lương", BigDecimal.TEN, null,
                Set.of(SALARY), null, BudgetPeriod.MONTHLY, null, null, null)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CATEGORY");
        assertThatThrownBy(() -> service.create(OWNER, new BudgetInput("Ví lạ", BigDecimal.TEN, null, Set.of(),
                UUID.randomUUID(), BudgetPeriod.MONTHLY, null, null, null)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("NOT_FOUND");
    }

    @Test
    void expensesInASubCategoryTriggerTheWarningOnceThenTheExceededAlert() {
        service.create(OWNER, new BudgetInput("Ăn uống", new BigDecimal("1000000"), null, Set.of(FOOD), null,
                BudgetPeriod.MONTHLY, null, null, null));

        spending.add(Money.of(850_000, "VND"));
        service.onExpense(expense(COFFEE, TODAY));
        service.onExpense(expense(COFFEE, TODAY));
        spending.add(Money.of(200_000, "VND"));
        service.onExpense(expense(FOOD, TODAY));
        service.onExpense(expense(SALARY, TODAY)); // not covered by the budget

        assertThat(events).hasSize(2);
        assertThat(events).first().isInstanceOfSatisfying(BudgetThresholdReached.class,
                e -> assertThat(e.percent()).isEqualTo(80));
        assertThat(events).last().isInstanceOfSatisfying(BudgetThresholdReached.class, e -> {
            assertThat(e.percent()).isEqualTo(100);
            assertThat(e.spent()).isEqualByComparingTo("1050000");
        });
    }

    @Test
    void expensesOutsideTheCurrentPeriodOrWalletAreIgnored() {
        service.create(OWNER, new BudgetInput("Ví tiền mặt", new BigDecimal("100"), null, Set.of(), CASH,
                BudgetPeriod.MONTHLY, null, null, null));
        spending.add(Money.of(1_000, "VND"));

        service.onExpense(expense(FOOD, TODAY.minusMonths(1)));
        service.onExpense(new TransactionSnapshot(UUID.randomUUID(), OWNER, UUID.randomUUID(), "EXPENSE",
                BigDecimal.TEN, BigDecimal.TEN.negate(), "VND", FOOD, TODAY, false, null, null, null, null));

        assertThat(events).isEmpty();
    }

    private static TransactionSnapshot expense(UUID categoryId, LocalDate date) {
        return new TransactionSnapshot(UUID.randomUUID(), OWNER, CASH, "EXPENSE", BigDecimal.TEN,
                BigDecimal.TEN.negate(), "VND", categoryId, date, false, null, null, null, null);
    }

    /** Ledger with fixed categories and wallet; expense totals = whatever the test put in {@code spending}. */
    private class FakeLedger implements LedgerQueryApi {
        @Override
        public List<WalletBalance> walletBalances(UUID ownerId) {
            return List.of(new WalletBalance(CASH, "Tiền mặt", "CASH", Money.of(0, "VND"), false, false));
        }

        @Override
        public List<CategoryView> categories(UUID ownerId) {
            return List.of(new CategoryView(FOOD, "Ăn uống", "EXPENSE", null),
                    new CategoryView(COFFEE, "Cà phê", "EXPENSE", FOOD),
                    new CategoryView(SALARY, "Lương", "INCOME", null));
        }

        @Override
        public List<Money> expenseTotals(UUID ownerId, Set<UUID> categoryIds, UUID walletId, LocalDate from,
                                         LocalDate to) {
            Map<String, Money> sums = new LinkedHashMap<>();
            spending.forEach(m -> sums.merge(m.currencyCode(), m, Money::plus));
            return List.copyOf(sums.values());
        }
    }

    /** 1 USD = 25 000 VND, nothing else. */
    private static class FakeRates implements CurrencyConversionApi {
        @Override
        public Optional<BigDecimal> rate(UUID ownerId, String from, String to, LocalDate asOf) {
            return from.equals("USD") && to.equals("VND") ? Optional.of(new BigDecimal("25000")) : Optional.empty();
        }

        @Override
        public Money convert(UUID ownerId, Money amount, String targetCurrency, LocalDate asOf) {
            throw new UnsupportedOperationException();
        }
    }

    private static class InMemoryBudgets implements BudgetRepository {
        final Map<UUID, Budget> store = new LinkedHashMap<>();

        @Override
        public Budget save(Budget budget) {
            store.put(budget.getId(), budget);
            return budget;
        }

        @Override
        public Optional<Budget> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(b -> b.getOwnerId().equals(ownerId));
        }

        @Override
        public List<Budget> findByOwner(UUID ownerId) {
            return store.values().stream().filter(b -> b.getOwnerId().equals(ownerId)).toList();
        }

        @Override
        public void delete(Budget budget) {
            store.remove(budget.getId());
        }

        @Override
        public void deleteByOwner(UUID ownerId) {
            store.values().removeIf(b -> b.getOwnerId().equals(ownerId));
        }
    }
}
