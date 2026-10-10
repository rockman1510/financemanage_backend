package com.financemanage.deposit.infrastructure.config;

import com.financemanage.deposit.domain.policy.CompoundMonthlyInterestCalculator;
import com.financemanage.deposit.domain.policy.InterestCalculator;
import com.financemanage.deposit.domain.policy.InterestCalculatorRegistry;
import com.financemanage.deposit.domain.policy.Simple30360InterestCalculator;
import com.financemanage.deposit.domain.policy.SimpleActual365InterestCalculator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free domain strategies. Any extra {@link InterestCalculator} bean is picked up too. */
@Configuration(proxyBeanMethods = false)
class DepositModuleConfig {

    @Bean
    SimpleActual365InterestCalculator simpleActual365InterestCalculator() {
        return new SimpleActual365InterestCalculator();
    }

    @Bean
    Simple30360InterestCalculator simple30360InterestCalculator() {
        return new Simple30360InterestCalculator();
    }

    @Bean
    CompoundMonthlyInterestCalculator compoundMonthlyInterestCalculator() {
        return new CompoundMonthlyInterestCalculator();
    }

    @Bean
    InterestCalculatorRegistry interestCalculatorRegistry(List<InterestCalculator> calculators) {
        return new InterestCalculatorRegistry(calculators);
    }
}
