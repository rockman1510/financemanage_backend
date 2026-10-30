package com.financemanage.deposit.domain.model;

/** State of the wallet side of opening or settling a deposit, recorded by the ledger. */
public enum WalletLinkStatus {
    /** Sent to the ledger, waiting for its answer */
    PENDING,
    RECORDED,
    /** Refused by the ledger (e.g. archived wallet); the wallet balance did not change */
    FAILED
}
