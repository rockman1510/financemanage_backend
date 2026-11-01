package com.financemanage.bill.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Paying the current due date; every field defaults to the bill's (amount, wallet) or today. */
public record PayBillInput(BigDecimal amount, UUID walletId, LocalDate paidOn) {
}
