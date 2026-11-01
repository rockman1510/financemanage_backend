package com.financemanage;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Modular monolith: assembles the identity, ledger, currency, budget, bill, deposit, loan, notification and
 * reporting modules.
 */
@SpringBootApplication
@Modulithic(systemName = "Finance Manage", sharedModules = "shared")
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
@EnableAsync
@EnableCaching
public class FinanceManageApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceManageApplication.class, args);
    }
}
