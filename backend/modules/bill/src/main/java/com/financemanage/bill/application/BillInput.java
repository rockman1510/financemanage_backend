package com.financemanage.bill.application;

import com.financemanage.bill.domain.model.BillFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Input for a new bill; the amount is in the wallet's currency and the category must be an expense category. */
public record BillInput(String name, BigDecimal amount, UUID walletId, UUID categoryId, BillFrequency frequency,
                        Integer interval, LocalDate firstDueDate, LocalDate endDate, Integer remindDaysBefore,
                        String note) {
}
