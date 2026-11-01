package com.financemanage.ledger.application;

import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.model.PageResult;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.TransactionFilter;
import com.financemanage.ledger.domain.model.TransactionTotals;
import com.financemanage.ledger.domain.model.TransactionType;
import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves the module's public {@link LedgerQueryApi}. */
@Service
@Transactional(readOnly = true)
public class LedgerQueryService implements LedgerQueryApi {

    private static final int SNAPSHOT_PAGE = 500;

    private final WalletService wallets;
    private final TransactionService transactions;
    private final CategoryRepository categories;

    public LedgerQueryService(WalletService wallets, TransactionService transactions,
                              CategoryRepository categories) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.categories = categories;
    }

    @Override
    public List<WalletBalance> walletBalances(UUID ownerId) {
        return wallets.walletBalances(ownerId);
    }

    @Override
    public List<CategoryView> categories(UUID ownerId) {
        return categories.findByOwner(ownerId, null).stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getType().name(), c.getParentId()))
                .toList();
    }

    @Override
    public List<Money> expenseTotals(UUID ownerId, Set<UUID> categoryIds, UUID walletId, LocalDate from,
                                     LocalDate to) {
        return transactions.totals(new TransactionFilter(ownerId, walletId, categoryIds,
                        Set.of(TransactionType.EXPENSE), from, to, null, null, null)).stream()
                .map(TransactionTotals::expense)
                .filter(Money::isPositive)
                .toList();
    }

    @Override
    public List<TransactionSnapshot> transactions(UUID ownerId) {
        TransactionFilter all = new TransactionFilter(ownerId, null, null, null, null, null, null, null, null);
        List<TransactionSnapshot> snapshots = new ArrayList<>();
        for (int page = 0; ; page++) {
            PageResult<Transaction> result = transactions.search(all, page, SNAPSHOT_PAGE);
            result.items().forEach(t -> snapshots.add(TransactionService.snapshot(t)));
            if (result.items().size() < SNAPSHOT_PAGE) {
                return snapshots;
            }
        }
    }
}
