package com.financemanage.notification.domain;

public enum NotificationType {
    DEPOSIT_MATURING_SOON, DEPOSIT_MATURED, INSTALLMENT_DUE_SOON, INSTALLMENT_OVERDUE, LOAN_PAID_OFF,
    /** Budget reached its warning level (80% by default) */
    BUDGET_WARNING,
    BUDGET_EXCEEDED,
    BILL_DUE_SOON,
    BILL_OVERDUE,
    /** The ledger refused a bill payment; the bill is due again */
    BILL_PAYMENT_FAILED,
    /** Evening reminder to record the day's spending (only when nothing was recorded that day) */
    DAILY_REMINDER
}
