package com.financemanage.saving.api;

import java.math.BigDecimal;
import java.util.UUID;

/** Domain events published by the saving module. Primitive payloads keep them easy to serialize (Kafka-ready). */
public final class SavingEvents {

    private SavingEvents() {
    }

    /** The goal reached {@code percent} (50 or 100) of its target; published once per milestone. */
    public record SavingGoalMilestone(UUID goalId, UUID ownerId, String name, int percent, BigDecimal saved,
                                      BigDecimal target, String currency) {
    }

    /** Money moved in or out of a goal (after the ledger recorded the wallet side). */
    public record SavingGoalChanged(UUID goalId, UUID ownerId) {
    }
}
