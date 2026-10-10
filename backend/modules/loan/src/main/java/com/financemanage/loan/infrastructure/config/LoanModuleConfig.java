package com.financemanage.loan.infrastructure.config;

import com.financemanage.loan.domain.policy.AnnuityScheduleGenerator;
import com.financemanage.loan.domain.policy.BulletScheduleGenerator;
import com.financemanage.loan.domain.policy.EqualPrincipalScheduleGenerator;
import com.financemanage.loan.domain.policy.InterestFreeScheduleGenerator;
import com.financemanage.loan.domain.policy.RepaymentScheduleGenerator;
import com.financemanage.loan.domain.policy.ScheduleGeneratorRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free schedule strategies. Any extra {@link RepaymentScheduleGenerator} bean is picked up. */
@Configuration(proxyBeanMethods = false)
class LoanModuleConfig {

    @Bean
    EqualPrincipalScheduleGenerator equalPrincipalScheduleGenerator() {
        return new EqualPrincipalScheduleGenerator();
    }

    @Bean
    AnnuityScheduleGenerator annuityScheduleGenerator() {
        return new AnnuityScheduleGenerator();
    }

    @Bean
    BulletScheduleGenerator bulletScheduleGenerator() {
        return new BulletScheduleGenerator();
    }

    @Bean
    InterestFreeScheduleGenerator interestFreeScheduleGenerator() {
        return new InterestFreeScheduleGenerator();
    }

    @Bean
    ScheduleGeneratorRegistry scheduleGeneratorRegistry(List<RepaymentScheduleGenerator> generators) {
        return new ScheduleGeneratorRegistry(generators);
    }
}
