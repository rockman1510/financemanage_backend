package com.financemanage.bill.infrastructure.scheduler;

import com.financemanage.bill.application.BillService;
import java.time.Clock;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily job: reminders for bills coming due and alerts for bills that became overdue. ShedLock avoids duplicates. */
@Component
class BillReminderJob {

    private static final Logger log = LoggerFactory.getLogger(BillReminderJob.class);

    private final BillService service;
    private final Clock clock;

    BillReminderJob(BillService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.bill.reminder-cron:0 20 0 * * *}")
    @SchedulerLock(name = "bill-reminder", lockAtMostFor = "PT30M")
    void run() {
        int sent = service.remind(LocalDate.now(clock));
        log.info("Bill reminder job: {} reminders", sent);
    }
}
