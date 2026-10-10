package com.financemanage.deposit.domain.model;

public enum RolloverPolicy {
    /** Không tái tục: deposit becomes MATURED */
    NONE,
    /** Tái tục gốc: interest paid out, principal rolls into a new term */
    PRINCIPAL_ONLY,
    /** Tái tục gốc + lãi */
    PRINCIPAL_AND_INTEREST
}
