package com.financemanage.notification.infrastructure.scheduler;

import com.financemanage.notification.application.NotificationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly job: users whose reminder hour has come in their time zone and who recorded nothing today get the
 * "record your spending" reminder. ShedLock and the per-day dedup key keep it to one per day.
 */
@Component
class DailyReminderJob {

    private static final Logger log = LoggerFactory.getLogger(DailyReminderJob.class);

    private final NotificationService service;

    DailyReminderJob(NotificationService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.notification.daily-reminder-cron:0 0 * * * *}")
    @SchedulerLock(name = "notification-daily-reminder", lockAtMostFor = "PT30M")
    void run() {
        log.info("Daily reminder job: {} reminders", service.sendDailyReminders());
    }
}
