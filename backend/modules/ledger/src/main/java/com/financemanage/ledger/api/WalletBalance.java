package com.financemanage.ledger.api;

import com.financemanage.shared.domain.Money;
import java.util.UUID;

public record WalletBalance(UUID walletId, String name, String type, Money balance, boolean excludedFromTotal,
                            boolean archived) {
}
