package com.financemanage.reporting.application;

import com.financemanage.deposit.api.DepositQueryApi;
import com.financemanage.investment.api.InvestmentQueryApi;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.loan.api.LoanQueryApi;
import com.financemanage.saving.api.SavingQueryApi;
import com.financemanage.shared.domain.Money;
import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Collects everything the owner has and owes from the other modules' query APIs, in their own currencies. The result
 * is cached per owner and day (in memory or Redis) and evicted by the events that change it; conversion to one
 * currency happens per request in {@link ReportService}.
 */
@Component
public class NetWorthCalculator {

    public static final String CACHE = "net-worth";

    private final LedgerQueryApi ledger;
    private final SavingQueryApi savings;
    private final InvestmentQueryApi investments;
    private final DepositQueryApi deposits;
    private final LoanQueryApi loans;
    private final CacheManager cacheManager;
    private final Clock clock;

    @SuppressWarnings("java:S107") // one query facade per module
    public NetWorthCalculator(LedgerQueryApi ledger, SavingQueryApi savings, InvestmentQueryApi investments,
                              DepositQueryApi deposits, LoanQueryApi loans, CacheManager cacheManager, Clock clock) {
        this.ledger = ledger;
        this.savings = savings;
        this.investments = investments;
        this.deposits = deposits;
        this.loans = loans;
        this.cacheManager = cacheManager;
        this.clock = clock;
    }

    public enum Kind {
        WALLET, SAVING, INVESTMENT, DEPOSIT, LENT, BORROWED
    }

    /** A thing owned (or owed, for BORROWED); {@code amount} is positive. */
    public record Item(Kind kind, UUID id, String name, Money amount) implements Serializable {
    }

    public record Items(LocalDate asOf, List<Item> items) implements Serializable {
    }

    public Items items(UUID ownerId) {
        LocalDate today = LocalDate.now(clock);
        Cache cache = cacheManager.getCache(CACHE);
        String key = key(ownerId, today);
        Items cached = cache == null ? null : cache.get(key, Items.class);
        if (cached != null) {
            return cached;
        }
        Items items = new Items(today, collect(ownerId, today));
        if (cache != null) {
            cache.put(key, items);
        }
        return items;
    }

    public void evict(UUID ownerId) {
        Cache cache = cacheManager.getCache(CACHE);
        if (cache != null) {
            cache.evict(key(ownerId, LocalDate.now(clock)));
        }
    }

    private List<Item> collect(UUID ownerId, LocalDate today) {
        List<Item> items = new ArrayList<>();
        ledger.walletBalances(ownerId).stream().filter(w -> !w.excludedFromTotal() && !w.balance().isZero())
                .forEach(w -> items.add(new Item(Kind.WALLET, w.walletId(), w.name(), w.balance())));
        savings.savedAmounts(ownerId).stream().filter(s -> s.saved().isPositive())
                .forEach(s -> items.add(new Item(Kind.SAVING, s.goalId(), s.name(), s.saved())));
        investments.positions(ownerId)
                .forEach(p -> items.add(new Item(Kind.INVESTMENT, p.assetId(), p.name(), p.marketValue())));
        deposits.activePositions(ownerId, today).forEach(d -> items.add(new Item(Kind.DEPOSIT, d.depositId(),
                d.institutionName(), d.principal().plus(d.accruedInterest()))));
        loans.activePositions(ownerId, today).forEach(l -> items.add(new Item(l.borrowed() ? Kind.BORROWED
                : Kind.LENT, l.loanId(), l.counterpartyName(), l.outstandingPrincipal())));
        return items;
    }

    private static String key(UUID ownerId, LocalDate day) {
        return ownerId + ":" + day;
    }
}
