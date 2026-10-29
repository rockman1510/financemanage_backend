package com.financemanage.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Partial update of an income or expense; {@code null} means unchanged and an empty text clears it. */
public record TransactionChanges(UUID walletId, UUID categoryId, BigDecimal amount, LocalDate occurredOn, String note,
                                 String withPerson, Boolean excludeFromReport) {
}
