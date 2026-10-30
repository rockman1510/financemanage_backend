package com.financemanage.budget.application;

import com.financemanage.budget.api.BudgetEvents.BudgetThresholdReached;
import com.financemanage.budget.domain.BudgetRepository;
import com.financemanage.budget.domain.model.Budget;
import com.financemanage.budget.domain.model.Budget.NewBudget;
import com.financemanage.budget.domain.model.BudgetStatus;
import com.financemanage.budget.domain.model.PeriodRange;
import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Budget use cases. Spending is read from the ledger; alerts are published once per level and period. */
@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgets;
    private final LedgerQueryApi ledger;
    private final CurrencyConversionApi conversion;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int warningPercent;

    public BudgetService(BudgetRepository budgets, LedgerQueryApi ledger, CurrencyConversionApi conversion,
                         ApplicationEventPublisher events, Clock clock,
                         @Value("${app.budget.warning-percent:80}") int warningPercent) {
        this.budgets = budgets;
        this.ledger = ledger;
        this.conversion = conversion;
        this.events = events;
        this.clock = clock;
        this.warningPercent = warningPercent;
    }

    public BudgetView create(UUID ownerId, BudgetInput input) {
        Set<UUID> categories = validCategories(ownerId, input.categoryIds());
        requireWallet(ownerId, input.walletId());
        LocalDate start = input.startDate() != null || input.period() == null ? input.startDate()
                : input.period().naturalStart(today());
        Budget budget = Budget.create(new NewBudget(ownerId, input.name(), input.amount() == null ? null
                : Money.of(input.amount(), input.currency() == null ? "VND" : input.currency()), categories,
                input.walletId(), input.period(), start, input.endDate(), !Boolean.FALSE.equals(input.repeat())));
        return view(budgets.save(budget), today());
    }

    @Transactional(readOnly = true)
    public List<BudgetView> list(UUID ownerId, LocalDate date) {
        LocalDate asOf = date != null ? date : today();
        return budgets.findByOwner(ownerId).stream().map(b -> view(b, asOf)).toList();
    }

    @Transactional(readOnly = true)
    public BudgetView get(UUID id, UUID ownerId, LocalDate date) {
        return view(load(id, ownerId), date != null ? date : today());
    }

    public BudgetView update(UUID id, UUID ownerId, BudgetChanges changes) {
        Budget budget = load(id, ownerId);
        Set<UUID> categories = changes.categoryIds() == null ? null : validCategories(ownerId, changes.categoryIds());
        requireWallet(ownerId, changes.walletId());
        budget.update(changes.name(), changes.amount(), categories, changes.walletId(),
                Boolean.TRUE.equals(changes.allWallets()), changes.repeat());
        return view(budgets.save(budget), today());
    }

    public void delete(UUID id, UUID ownerId) {
        budgets.delete(load(id, ownerId));
    }

    /**
     * Checks the owner's budgets after an expense was recorded or changed today's period, and publishes an alert
     * for each budget that newly reached its warning level or was exceeded.
     */
    public void onExpense(TransactionSnapshot expense) {
        LocalDate today = today();
        Map<UUID, CategoryView> categories = null;
        for (Budget budget : budgets.findByOwner(expense.ownerId())) {
            Optional<PeriodRange> period = budget.periodAt(today);
            if (period.isEmpty() || !period.get().contains(expense.occurredOn())
                    || (budget.getWalletId() != null && !budget.getWalletId().equals(expense.walletId()))) {
                continue;
            }
            if (!budget.getCategoryIds().isEmpty()) {
                if (categories == null) {
                    categories = ledger.categories(expense.ownerId()).stream()
                            .collect(Collectors.toMap(CategoryView::id, Function.identity()));
                }
                if (!covers(budget, expense.categoryId(), categories)) {
                    continue;
                }
            }
            BudgetStatus status = view(budget, today).status();
            int level = budget.newAlertLevel(period.get(), status.percent(), warningPercent);
            budgets.save(budget);
            if (level > 0) {
                events.publishEvent(new BudgetThresholdReached(budget.getId(), budget.getOwnerId(), budget.getName(),
                        level, status.spent().roundedAmount(), budget.getAmount().roundedAmount(),
                        budget.getAmount().currencyCode(), period.get().start(), period.get().end()));
            }
        }
    }

    public void deleteAllOf(UUID ownerId) {
        budgets.deleteByOwner(ownerId);
    }

    private BudgetView view(Budget budget, LocalDate date) {
        Money spent = Money.zero(budget.getAmount().currency());
        Set<String> unconverted = new TreeSet<>();
        Optional<PeriodRange> period = budget.periodAt(date);
        if (period.isPresent()) {
            LocalDate end = period.get().end();
            for (Money total : ledger.expenseTotals(budget.getOwnerId(), budget.getCategoryIds(),
                    budget.getWalletId(), period.get().start(), end)) {
                if (total.currency().equals(spent.currency())) {
                    spent = spent.plus(total);
                } else {
                    Optional<BigDecimal> rate = conversion.rate(budget.getOwnerId(), total.currencyCode(),
                            spent.currencyCode(), end.isAfter(date) ? date : end);
                    if (rate.isPresent()) {
                        spent = spent.plus(new Money(total.amount().multiply(rate.get()), spent.currency()).rounded());
                    } else {
                        unconverted.add(total.currencyCode());
                    }
                }
            }
        }
        return new BudgetView(budget, budget.status(date, spent, warningPercent), unconverted);
    }

    /** Whether the budget counts {@code categoryId}: one of its categories or a sub-category of one. */
    private static boolean covers(Budget budget, UUID categoryId, Map<UUID, CategoryView> categories) {
        if (categoryId == null) {
            return false;
        }
        CategoryView category = categories.get(categoryId);
        return budget.getCategoryIds().contains(categoryId)
                || (category != null && category.parentId() != null
                && budget.getCategoryIds().contains(category.parentId()));
    }

    /** Only the owner's expense categories can be budgeted. */
    private Set<UUID> validCategories(UUID ownerId, Set<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> expenses = new HashSet<>();
        ledger.categories(ownerId).stream().filter(c -> "EXPENSE".equals(c.type())).forEach(c -> expenses.add(c.id()));
        if (!expenses.containsAll(categoryIds)) {
            throw new DomainException("INVALID_CATEGORY", "Budgets can only contain your expense categories");
        }
        return Set.copyOf(categoryIds);
    }

    private void requireWallet(UUID ownerId, UUID walletId) {
        if (walletId != null && ledger.walletBalances(ownerId).stream().noneMatch(w -> w.walletId().equals(walletId))) {
            throw new NotFoundException("Wallet", walletId);
        }
    }

    private Budget load(UUID id, UUID ownerId) {
        return budgets.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Budget", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
