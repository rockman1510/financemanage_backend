package com.financemanage.ledger.domain.model;

/** Kind of a transaction; the sign says whether it adds to (+1) or takes from (-1) the wallet balance. */
public enum TransactionType {
    INCOME(1),
    EXPENSE(-1),
    TRANSFER_IN(1),
    TRANSFER_OUT(-1),
    /** Điều chỉnh số dư tăng */
    ADJUSTMENT_IN(1),
    /** Điều chỉnh số dư giảm */
    ADJUSTMENT_OUT(-1);

    private final int sign;

    TransactionType(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }

    public boolean isTransfer() {
        return this == TRANSFER_IN || this == TRANSFER_OUT;
    }

    /** Income and expenses carry a category and can be edited; transfers and adjustments cannot. */
    public boolean isCategorized() {
        return this == INCOME || this == EXPENSE;
    }

    public static TransactionType of(CategoryType categoryType) {
        return categoryType == CategoryType.INCOME ? INCOME : EXPENSE;
    }
}
