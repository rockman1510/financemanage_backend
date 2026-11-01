package com.financemanage.investment.infrastructure.scheduler;

import com.financemanage.investment.application.InvestmentService;
import com.financemanage.investment.domain.model.Asset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Asks the price provider for the assets still held (off by default: prices are entered by hand). */
@Component
class PriceRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(PriceRefreshJob.class);

    private final InvestmentService service;

    PriceRefreshJob(InvestmentService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.investment.price-refresh-cron:-}")
    @SchedulerLock(name = "investment-price-refresh", lockAtMostFor = "PT30M")
    void run() {
        int updated = 0;
        for (Asset asset : service.assetsToPrice()) {
            try {
                if (service.refreshPrice(asset.getId(), asset.getOwnerId())) {
                    updated++;
                }
            } catch (RuntimeException e) {
                log.warn("Price of asset {} not refreshed: {}", asset.getId(), e.getMessage());
            }
        }
        log.info("Price refresh job: {} assets updated", updated);
    }
}
