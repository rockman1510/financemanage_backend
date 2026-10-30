package com.financemanage.bill.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Partial update of a bill; {@code null} = unchanged. {@code active = false} pauses reminders. */
public record BillChanges(String name, BigDecimal amount, UUID walletId, UUID categoryId, LocalDate endDate,
                          Integer remindDaysBefore, String note, Boolean active) {
}
