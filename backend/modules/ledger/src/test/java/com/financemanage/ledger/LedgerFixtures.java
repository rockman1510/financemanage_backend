package com.financemanage.ledger;

import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.ledger.domain.model.Wallet.NewWallet;
import com.financemanage.ledger.domain.model.WalletType;
import com.financemanage.shared.domain.Money;
import java.util.UUID;

public final class LedgerFixtures {

    public static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private LedgerFixtures() {
    }

    public static Wallet wallet(String name, long initialBalance, String currency) {
        return Wallet.open(new NewWallet(OWNER, name, WalletType.CASH, Money.of(initialBalance, currency), null,
                false, null, null, null));
    }

    public static Wallet wallet(String name, long initialBalance) {
        return wallet(name, initialBalance, "VND");
    }

    public static Category expense(String name) {
        return Category.create(OWNER, name, CategoryType.EXPENSE, null, null);
    }

    public static Category income(String name) {
        return Category.create(OWNER, name, CategoryType.INCOME, null, null);
    }

    public static Money vnd(long amount) {
        return Money.of(amount, "VND");
    }
}
