package com.financemanage.loan.domain.model;

public enum RepaymentMethod {
    /** Dư nợ giảm dần – gốc đều mỗi kỳ */
    EQUAL_PRINCIPAL,
    /** Tổng trả đều mỗi kỳ (annuity) */
    ANNUITY,
    /** Trả lãi hằng tháng, gốc cuối kỳ */
    BULLET,
    /** Không lãi, gốc chia đều */
    INTEREST_FREE
}
