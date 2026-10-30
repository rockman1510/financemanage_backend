package com.financemanage.budget.application;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/** Partial update of a budget; {@code null} = unchanged, {@code allWallets = true} removes the wallet filter. */
public record BudgetChanges(String name, BigDecimal amount, Set<UUID> categoryIds, UUID walletId, Boolean allWallets,
                            Boolean repeat) {
}
