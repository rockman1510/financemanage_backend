package com.financemanage.deposit.domain.model;

public enum InterestCalculationMethod {
    /** Simple interest, actual days / 365 (most Vietnamese banks) */
    SIMPLE_ACTUAL_365,
    /** Simple interest, 30/360 day count */
    SIMPLE_30_360,
    /** Interest compounded monthly */
    COMPOUND_MONTHLY
}
