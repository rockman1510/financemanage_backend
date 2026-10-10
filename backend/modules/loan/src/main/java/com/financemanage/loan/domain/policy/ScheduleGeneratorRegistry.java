package com.financemanage.loan.domain.policy;

import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.shared.domain.DomainException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public class ScheduleGeneratorRegistry {

    private final Map<RepaymentMethod, RepaymentScheduleGenerator> generators = new EnumMap<>(RepaymentMethod.class);

    public ScheduleGeneratorRegistry(Collection<? extends RepaymentScheduleGenerator> generators) {
        generators.forEach(g -> this.generators.put(g.method(), g));
    }

    public RepaymentScheduleGenerator get(RepaymentMethod method) {
        RepaymentScheduleGenerator generator = generators.get(method);
        if (generator == null) {
            throw new DomainException("UNSUPPORTED_REPAYMENT_METHOD", "No schedule generator for " + method);
        }
        return generator;
    }
}
