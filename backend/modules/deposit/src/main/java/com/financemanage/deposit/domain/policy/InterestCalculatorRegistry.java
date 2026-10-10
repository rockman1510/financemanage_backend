package com.financemanage.deposit.domain.policy;

import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.shared.domain.DomainException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Resolves the {@link InterestCalculator} strategy for a deposit. */
public class InterestCalculatorRegistry {

    private final Map<InterestCalculationMethod, InterestCalculator> calculators =
            new EnumMap<>(InterestCalculationMethod.class);

    public InterestCalculatorRegistry(Collection<? extends InterestCalculator> calculators) {
        calculators.forEach(c -> this.calculators.put(c.method(), c));
    }

    public InterestCalculator get(InterestCalculationMethod method) {
        InterestCalculator calculator = calculators.get(method);
        if (calculator == null) {
            throw new DomainException("UNSUPPORTED_INTEREST_METHOD", "No calculator registered for " + method);
        }
        return calculator;
    }
}
