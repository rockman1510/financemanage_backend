package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.Money;
import java.util.List;
import java.util.Set;

/**
 * Total balance of the wallets that count towards the total (not archived, not excluded), in one currency.
 * Wallets whose currency has no exchange rate are listed with {@code converted == null} and their currency is in
 * {@code missingRates}; they are not part of the total.
 */
public record WalletTotal(Money total, List<Line> wallets, Set<String> missingRates) {

    public record Line(Wallet wallet, Money converted) {
    }
}
