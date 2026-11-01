package com.financemanage.saving.domain.model;

public enum MovementStatus {
    /** Sent to the ledger, waiting for its answer */
    PENDING,
    /** The wallet side is recorded in the ledger */
    RECORDED,
    /** Refused by the ledger; the goal was rolled back */
    FAILED
}
