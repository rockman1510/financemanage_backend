package com.financemanage.deposit.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events published by the deposit module. Primitive payloads keep them easy to serialize (Kafka-ready). */
public final class DepositEvents {

    private DepositEvents() {
    }

    public record DepositOpened(UUID depositId, UUID ownerId, BigDecimal principal, String currency) {
    }

    public record DepositChanged(UUID depositId, UUID ownerId) {
    }

    public record DepositSettled(UUID depositId, UUID ownerId, BigDecimal totalAmount, String currency,
                                 boolean early) {
    }

    public record DepositMatured(UUID depositId, UUID ownerId, String institutionName, LocalDate maturityDate,
                                 boolean rolledOver, BigDecimal newPrincipal, String currency) {
    }

    public record DepositMaturingSoon(UUID depositId, UUID ownerId, String institutionName, LocalDate maturityDate,
                                      long daysLeft) {
    }
}
