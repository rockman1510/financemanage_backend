package com.financemanage.reporting.application;

import com.financemanage.deposit.api.DepositEvents.DepositChanged;
import com.financemanage.deposit.api.DepositEvents.DepositMatured;
import com.financemanage.deposit.api.DepositEvents.DepositOpened;
import com.financemanage.deposit.api.DepositEvents.DepositSettled;
import com.financemanage.investment.api.InvestmentEvents.PortfolioChanged;
import com.financemanage.ledger.api.LedgerEvents.TransactionDeleted;
import com.financemanage.ledger.api.LedgerEvents.TransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.TransactionUpdated;
import com.financemanage.ledger.api.LedgerEvents.WalletChanged;
import com.financemanage.ledger.api.LedgerEvents.WalletDeleted;
import com.financemanage.loan.api.LoanEvents.LoanChanged;
import com.financemanage.loan.api.LoanEvents.LoanCreated;
import com.financemanage.loan.api.LoanEvents.LoanPaidOff;
import com.financemanage.loan.api.LoanEvents.RepaymentRecorded;
import com.financemanage.saving.api.SavingEvents.SavingGoalChanged;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the cached dashboard and net worth consistent: any change in deposits or loans evicts both owner entries,
 * a change in wallets, savings or investments evicts the net worth.
 */
@Component
class DashboardCacheEvictor {

    private final CacheManager cacheManager;
    private final NetWorthCalculator netWorth;
    private final Clock clock;

    DashboardCacheEvictor(CacheManager cacheManager, NetWorthCalculator netWorth, Clock clock) {
        this.cacheManager = cacheManager;
        this.netWorth = netWorth;
        this.clock = clock;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(DepositOpened e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(DepositChanged e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(DepositSettled e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(DepositMatured e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(LoanCreated e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(LoanChanged e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(RepaymentRecorded e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(LoanPaidOff e) { evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TransactionRecorded e) { netWorth.evict(e.transaction().ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TransactionUpdated e) { netWorth.evict(e.after().ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TransactionDeleted e) { netWorth.evict(e.transaction().ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(WalletChanged e) { netWorth.evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(WalletDeleted e) { netWorth.evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(SavingGoalChanged e) { netWorth.evict(e.ownerId()); }

    @TransactionalEventListener(fallbackExecution = true)
    void on(PortfolioChanged e) { netWorth.evict(e.ownerId()); }

    void evict(UUID ownerId) {
        Cache cache = cacheManager.getCache(DashboardCalculator.CACHE);
        if (cache != null) {
            cache.evict(DashboardCalculator.cacheKey(ownerId, LocalDate.now(clock)));
        }
        netWorth.evict(ownerId);
    }
}
