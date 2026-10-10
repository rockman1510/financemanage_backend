package com.financemanage.loan.infrastructure.scheduler;

import com.financemanage.loan.application.LoanService;
import java.time.Clock;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily job: reminders for upcoming installments and alerts for newly overdue ones. */
@Component
class LoanReminderJob {

    private static final Logger log = LoggerFactory.getLogger(LoanReminderJob.class);

    private final LoanService service;
    private final Clock clock;
    private final int reminderDaysBefore;

    LoanReminderJob(LoanService service, Clock clock,
                    @Value("${app.loan.reminder-days-before:3}") int reminderDaysBefore) {
        this.service = service;
        this.clock = clock;
        this.reminderDaysBefore = reminderDaysBefore;
    }

    @Scheduled(cron = "${app.loan.reminder-cron:0 15 0 * * *}")
    @SchedulerLock(name = "loan-reminders", lockAtMostFor = "PT30M")
    void run() {
        LocalDate today = LocalDate.now(clock);
        int dueSoon = service.remindUpcomingInstallments(today, reminderDaysBefore);
        int overdue = service.flagNewlyOverdueInstallments(today);
        log.info("Loan reminder job: {} due soon, {} newly overdue", dueSoon, overdue);
    }
}
