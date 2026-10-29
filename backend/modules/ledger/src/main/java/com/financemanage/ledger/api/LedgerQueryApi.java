package com.financemanage.ledger.api;

import java.util.List;
import java.util.UUID;

/** Read-only facade used by other modules (e.g. reporting). */
public interface LedgerQueryApi {

    /** Every wallet of the owner, archived ones included. */
    List<WalletBalance> walletBalances(UUID ownerId);
}
