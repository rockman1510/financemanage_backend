package com.financemanage.deposit.infrastructure.scheduler;

import com.financemanage.deposit.application.DepositService;
import java.time.Clock;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily job: matures/rolls over deposits and sends maturity reminders. ShedLock prevents duplicate runs. */
@Component
class DepositMaturityJob {

    private static final Logger log = LoggerFactory.getLogger(DepositMaturityJob.class);

    private final DepositService service;
    private final Clock clock;
    private final int reminderDaysBefore;

    DepositMaturityJob(DepositService service, Clock clock,
                       @Value("${app.deposit.reminder-days-before:7}") int reminderDaysBefore) {
        this.service = service;
        this.clock = clock;
        this.reminderDaysBefore = reminderDaysBefore;
    }

    @Scheduled(cron = "${app.deposit.maturity-cron:0 5 0 * * *}")
    @SchedulerLock(name = "deposit-maturity", lockAtMostFor = "PT30M")
    void run() {
        LocalDate today = LocalDate.now(clock);
        int matured = service.processMaturities(today);
        int reminded = service.remindUpcomingMaturities(today, reminderDaysBefore);
        log.info("Deposit maturity job: {} matured, {} reminders", matured, reminded);
    }
}
