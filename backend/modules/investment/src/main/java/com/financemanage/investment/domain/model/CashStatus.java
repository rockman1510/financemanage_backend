package com.financemanage.investment.domain.model;

/** State of the wallet side of a trade. */
public enum CashStatus {
    /** No wallet given: the trade does not move money in the app */
    NONE,
    PENDING,
    RECORDED,
    /** Refused by the ledger; the position is kept, only the wallet did not change */
    FAILED
}
