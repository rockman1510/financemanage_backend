package com.financemanage.reporting.application;

import com.financemanage.deposit.api.DepositEvents.DepositChanged;
import com.financemanage.deposit.api.DepositEvents.DepositMatured;
import com.financemanage.deposit.api.DepositEvents.DepositOpened;
import com.financemanage.deposit.api.DepositEvents.DepositSettled;
import com.financemanage.loan.api.LoanEvents.LoanChanged;
import com.financemanage.loan.api.LoanEvents.LoanCreated;
import com.financemanage.loan.api.LoanEvents.LoanPaidOff;
import com.financemanage.loan.api.LoanEvents.RepaymentRecorded;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps the cached dashboard consistent: any change in deposits or loans evicts the owner's entry. */
@Component
class DashboardCacheEvictor {

    private final CacheManager cacheManager;
    private final Clock clock;

    DashboardCacheEvictor(CacheManager cacheManager, Clock clock) {
        this.cacheManager = cacheManager;
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

    void evict(UUID ownerId) {
        Cache cache = cacheManager.getCache(DashboardCalculator.CACHE);
        if (cache != null) {
            cache.evict(DashboardCalculator.cacheKey(ownerId, LocalDate.now(clock)));
        }
    }
}
