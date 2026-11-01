package com.financemanage.saving.infrastructure.scheduler;

import com.financemanage.saving.application.SavingService;
import com.financemanage.saving.domain.model.SavingGoal;
import java.time.Clock;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily job: automatic contributions that are due, one goal per database transaction. */
@Component
class AutoContributionJob {

    private static final Logger log = LoggerFactory.getLogger(AutoContributionJob.class);

    private final SavingService service;
    private final Clock clock;

    AutoContributionJob(SavingService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.saving.auto-contribution-cron:0 40 0 * * *}")
    @SchedulerLock(name = "saving-auto-contribution", lockAtMostFor = "PT30M")
    void run() {
        LocalDate today = LocalDate.now(clock);
        int done = 0;
        for (SavingGoal goal : service.goalsWithDueAutoContribution(today)) {
            try {
                service.runAutoContribution(goal.getId(), goal.getOwnerId(), today);
                done++;
            } catch (RuntimeException e) {
                log.warn("Automatic contribution of goal {} failed: {}", goal.getId(), e.getMessage());
            }
        }
        log.info("Automatic contribution job: {} goals", done);
    }
}
