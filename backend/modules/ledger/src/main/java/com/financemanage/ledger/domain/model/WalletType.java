package com.financemanage.ledger.domain.model;

public enum WalletType {
    /** Tiền mặt */
    CASH,
    /** Tài khoản ngân hàng (nhập tay, không kết nối ngân hàng) */
    BANK_ACCOUNT,
    /** Ví điện tử (MoMo, ZaloPay…) */
    E_WALLET,
    /** Thẻ tín dụng: balance is usually negative (the debt) */
    CREDIT_CARD,
    OTHER
}
