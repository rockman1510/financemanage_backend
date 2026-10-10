package com.financemanage.deposit.domain.model;

public enum InterestPaymentMethod {
    /** Lãi cuối kỳ */
    AT_MATURITY,
    /** Lãi hằng tháng (paid out, not added to principal on rollover) */
    MONTHLY,
    /** Lãi trả trước */
    UPFRONT
}
