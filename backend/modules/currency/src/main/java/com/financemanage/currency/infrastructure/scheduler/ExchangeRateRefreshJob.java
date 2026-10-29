package com.financemanage.currency.infrastructure.scheduler;

import com.financemanage.currency.application.ExchangeRateService;
import java.time.Clock;
import java.time.LocalDate;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily job: stores the rates of every registered {@code ExchangeRateProvider}. Does nothing without providers. */
@Component
class ExchangeRateRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateRefreshJob.class);

    private final ExchangeRateService service;
    private final Clock clock;

    ExchangeRateRefreshJob(ExchangeRateService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.currency.refresh-cron:0 0 6 * * *}")
    @SchedulerLock(name = "currency-refresh", lockAtMostFor = "PT10M")
    void run() {
        int stored = service.refreshFromProviders(LocalDate.now(clock));
        log.info("Exchange rate refresh job: {} rates stored", stored);
    }
}
