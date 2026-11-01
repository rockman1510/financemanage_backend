package com.financemanage.deposit.application;

import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.ExternalTransactionRejected;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** The ledger's answers to the wallet commands of opening and settling deposits. */
@Component
class DepositLedgerListener {

    private final DepositWalletService wallets;

    DepositLedgerListener(DepositWalletService wallets) {
        this.wallets = wallets;
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRecorded event) {
        if (isDeposit(event.source())) {
            wallets.answered(event.source(), event.sourceId(), true);
        }
    }

    @ApplicationModuleListener
    void on(ExternalTransactionRejected event) {
        if (isDeposit(event.source())) {
            wallets.answered(event.source(), event.sourceId(), false);
        }
    }

    private static boolean isDeposit(String source) {
        return DepositWalletService.SOURCE_OPEN.equals(source) || DepositWalletService.SOURCE_SETTLE.equals(source);
    }
}
