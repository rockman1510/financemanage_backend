package com.financemanage.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Input for recording an income or expense; the amount is in the wallet's currency, the date defaults to today. */
public record TransactionInput(UUID walletId, UUID categoryId, BigDecimal amount, LocalDate occurredOn, String note,
                               String withPerson, boolean excludeFromReport) {
}
