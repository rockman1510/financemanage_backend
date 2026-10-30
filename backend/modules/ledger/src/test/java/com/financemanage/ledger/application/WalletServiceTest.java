package com.financemanage.ledger.application;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.expense;
import static com.financemanage.ledger.LedgerFixtures.vnd;
import static com.financemanage.ledger.LedgerFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.api.LedgerEvents.WalletDeleted;
import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.Frequency;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.ledger.domain.model.Wallet.NewWallet;
import com.financemanage.ledger.domain.model.Wallet.WalletChanges;
import com.financemanage.ledger.domain.model.WalletType;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WalletServiceTest {

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final WalletService service = ledger.walletService;

    @Test
    void totalConvertsCurrenciesAndSkipsExcludedArchivedAndUnknownOnes() {
        service.open(spec("Tiền mặt", 1_000_000, "VND", false));
        service.open(spec("USD", 100, "USD", false));
        service.open(spec("Quỹ riêng", 9_000_000, "VND", true));
        service.open(spec("EUR", 50, "EUR", false));
        Wallet old = service.open(spec("Ví cũ", 7_000_000, "VND", false));
        service.update(old.getId(), OWNER, new WalletChanges(null, null, null, true, null, null, null, null));

        WalletTotal total = service.total(OWNER, "VND", null);

        assertThat(total.total()).isEqualTo(vnd(3_500_000));
        assertThat(total.wallets()).hasSize(3);
        assertThat(total.missingRates()).containsExactly("EUR");
        assertThat(service.walletBalances(OWNER)).hasSize(5);
    }

    @Test
    void walletsWithTransactionsCannotBeDeleted() {
        Wallet cash = ledger.add(wallet("Tiền mặt", 0));
        Category food = ledger.add(expense("Ăn uống"));
        ledger.transactionService.record(OWNER, new TransactionInput(cash.getId(), food.getId(),
                BigDecimal.TEN, null, null, null, false), null);

        assertThatThrownBy(() -> service.delete(cash.getId(), OWNER))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("WALLET_HAS_TRANSACTIONS");
    }

    @Test
    void deletingAnEmptyWalletRemovesItsRecurringRules() {
        Wallet cash = ledger.add(wallet("Tiền mặt", 0));
        Category food = ledger.add(expense("Ăn uống"));
        ledger.recurringService.create(OWNER, new RecurringInput(cash.getId(), food.getId(), BigDecimal.TEN, null,
                null, Frequency.DAILY, 1, InMemoryLedger.TODAY.plusDays(1), null));

        service.delete(cash.getId(), OWNER);

        assertThat(ledger.wallets.store).isEmpty();
        assertThat(ledger.rules.store).isEmpty();
        assertThat(ledger.events).last().isInstanceOf(WalletDeleted.class);
    }

    private static NewWallet spec(String name, long balance, String currency, boolean excluded) {
        return new NewWallet(OWNER, name, WalletType.BANK_ACCOUNT, Money.of(balance, currency), null, excluded, null,
                null, null);
    }
}
