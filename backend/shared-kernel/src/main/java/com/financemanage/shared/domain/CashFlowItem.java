package com.financemanage.shared.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An expected future money movement for the owner (e.g. a deposit maturing or a loan installment due).
 * Published by domain modules and aggregated by reporting.
 */
public record CashFlowItem(
        LocalDate date,
        Money amount,
        Direction direction,
        String sourceType,
        UUID sourceId,
        String title) {

    public enum Direction { IN, OUT }
}
