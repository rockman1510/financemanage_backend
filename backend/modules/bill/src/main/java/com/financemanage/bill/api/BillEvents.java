package com.financemanage.bill.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Domain events published by the bill module. Primitive payloads keep them easy to serialize (Kafka-ready). */
public final class BillEvents {

    private BillEvents() {
    }

    /** The next due date is {@code daysLeft} days away (the bill's reminder setting). */
    public record BillDueSoon(UUID billId, UUID ownerId, String name, BigDecimal amount, String currency,
                              LocalDate dueDate, long daysLeft) {
    }

    /** The due date passed yesterday and the bill was not paid. */
    public record BillOverdue(UUID billId, UUID ownerId, String name, BigDecimal amount, String currency,
                              LocalDate dueDate) {
    }

    /** A payment was recorded in the ledger as {@code transactionId}. */
    public record BillPaid(UUID billId, UUID ownerId, UUID paymentId, String name, BigDecimal amount, String currency,
                           LocalDate paidOn, UUID transactionId) {
    }

    /** The ledger refused a payment (e.g. archived wallet); the bill is due again. */
    public record BillPaymentFailed(UUID billId, UUID ownerId, UUID paymentId, String name, String reason) {
    }
}
