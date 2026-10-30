package com.financemanage.ledger.domain;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.vnd;
import static com.financemanage.ledger.LedgerFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.ledger.domain.model.Wallet.NewWallet;
import com.financemanage.ledger.domain.model.Wallet.WalletChanges;
import com.financemanage.ledger.domain.model.WalletType;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WalletTest {

    @Test
    void opensWithTheInitialBalance() {
        Wallet wallet = wallet("  Tiền mặt ", 500_000);

        assertThat(wallet.getName()).isEqualTo("Tiền mặt");
        assertThat(wallet.getBalance()).isEqualTo(vnd(500_000));
        assertThat(wallet.isArchived()).isFalse();
    }

    @Test
    void changingTheInitialBalanceShiftsTheBalance() {
        Wallet wallet = wallet("Ví", 500_000);
        wallet.apply(vnd(-200_000));

        wallet.update(new WalletChanges(null, null, null, null, new BigDecimal("1000000"), null, null, null));

        assertThat(wallet.getInitialBalance()).isEqualTo(vnd(1_000_000));
        assertThat(wallet.getBalance()).isEqualTo(vnd(800_000));
    }

    @Test
    void archivedWalletsAreNotUsable() {
        Wallet wallet = wallet("Ví", 0);
        wallet.update(new WalletChanges(null, null, null, true, null, null, null, null));

        assertThatThrownBy(wallet::requireUsable)
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("WALLET_ARCHIVED");
    }

    @Test
    void rejectsAmountsFinerThanTheCurrencyAndForeignCurrencies() {
        assertThatThrownBy(() -> wallet("Ví", 0).requireCurrency(Money.of(1, "USD")))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
        assertThatThrownBy(() -> Wallet.open(new NewWallet(OWNER, "Ví", WalletType.CASH, Money.of("10.5", "VND"),
                null, false, null, null, null)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void creditCardsTrackTheAvailableCredit() {
        Wallet card = Wallet.open(new NewWallet(OWNER, "Visa", WalletType.CREDIT_CARD, vnd(0), "card", false,
                vnd(20_000_000), 25, 10));
        card.apply(vnd(-3_000_000));

        assertThat(card.availableCredit()).isEqualTo(vnd(17_000_000));
        assertThat(card.getStatementDay()).isEqualTo(25);
        assertThatThrownBy(() -> card.update(new WalletChanges(null, null, null, null, null, null, 32, null)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_DAY_OF_MONTH");
    }

    @Test
    void onlyCreditCardsKeepCreditTerms() {
        Wallet cash = Wallet.open(new NewWallet(OWNER, "Ví", WalletType.CASH, vnd(0), null, false, vnd(1_000), 5,
                10));

        assertThat(cash.getCreditLimit()).isNull();
        assertThat(cash.availableCredit()).isNull();
        assertThat(cash.getStatementDay()).isNull();
    }
}
