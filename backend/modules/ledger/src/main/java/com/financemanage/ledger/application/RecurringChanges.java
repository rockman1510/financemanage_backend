package com.financemanage.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Partial update of a recurring rule; {@code null} means unchanged. {@code active = false} pauses the rule and
 * {@code true} resumes it without back-filling missed occurrences.
 */
public record RecurringChanges(UUID walletId, UUID categoryId, BigDecimal amount, String note, String withPerson,
                               LocalDate endDate, Boolean active) {
}
