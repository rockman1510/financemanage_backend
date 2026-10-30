package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.model.Frequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Input for a recurring transaction rule; the amount is in the wallet's currency. */
public record RecurringInput(UUID walletId, UUID categoryId, BigDecimal amount, String note, String withPerson,
                             Frequency frequency, int interval, LocalDate startDate, LocalDate endDate) {
}
