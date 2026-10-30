package com.financemanage.bill.domain.model;

public enum PaymentStatus {
    /** Sent to the ledger, waiting for its answer */
    PENDING,
    /** Recorded as an expense in the ledger */
    RECORDED,
    /** Refused by the ledger; the bill was rolled back */
    FAILED,
    /** The user skipped this due date without paying */
    SKIPPED
}
