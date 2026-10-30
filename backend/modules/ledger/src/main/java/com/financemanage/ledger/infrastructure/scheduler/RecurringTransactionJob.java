package com.financemanage.ledger.infrastructure.scheduler;

import com.financemanage.ledger.application.RecurringTransactionService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job: records the due occurrences of recurring transactions, one rule per database transaction so a failing
 * rule does not block the others. ShedLock prevents duplicate runs; idempotency keys prevent duplicate records.
 */
@Component
class RecurringTransactionJob {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionJob.class);

    private final RecurringTransactionService service;
    private final Clock clock;

    RecurringTransactionJob(RecurringTransactionService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.ledger.recurring-cron:0 30 0 * * *}")
    @SchedulerLock(name = "ledger-recurring", lockAtMostFor = "PT30M")
    void run() {
        LocalDate today = LocalDate.now(clock);
        int failed = 0;
        List<UUID> due = service.dueRuleIds(today);
        for (UUID ruleId : due) {
            try {
                service.recordDue(ruleId, today);
            } catch (RuntimeException e) {
                failed++;
                log.warn("Recurring transaction {} failed: {}", ruleId, e.getMessage());
            }
        }
        log.info("Recurring transaction job: {} rules processed, {} failed", due.size(), failed);
    }
}
