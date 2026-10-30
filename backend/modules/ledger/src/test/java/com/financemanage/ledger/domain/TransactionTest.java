package com.financemanage.ledger.domain;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.expense;
import static com.financemanage.ledger.LedgerFixtures.income;
import static com.financemanage.ledger.LedgerFixtures.vnd;
import static com.financemanage.ledger.LedgerFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.Transaction.NewTransaction;
import com.financemanage.ledger.domain.model.Transaction.TransferLegs;
import com.financemanage.ledger.domain.model.TransactionType;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final LocalDate DAY = LocalDate.of(2025, 4, 1);

    private final Wallet cash = wallet("Tiền mặt", 1_000_000);

    @Test
    void theCategoryDecidesIncomeOrExpense() {
        Transaction lunch = Transaction.record(spec(vnd(50_000)), cash, expense("Ăn uống"));
        Transaction salary = Transaction.record(spec(vnd(20_000_000)), cash, income("Lương"));

        assertThat(lunch.getType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(lunch.signedAmount()).isEqualTo(vnd(-50_000));
        assertThat(salary.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(salary.signedAmount()).isEqualTo(vnd(20_000_000));
    }

    @Test
    void validatesAmountWalletAndOwnership() {
        Category food = expense("Ăn uống");
        assertThatThrownBy(() -> Transaction.record(spec(vnd(0)), cash, food))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_AMOUNT");
        assertThatThrownBy(() -> Transaction.record(spec(Money.of(5, "USD")), cash, food))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");

        Category foreign = Category.create(UUID.randomUUID(), "Khác", food.getType(), null, null);
        assertThatThrownBy(() -> Transaction.record(spec(vnd(1)), cash, foreign))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("FOREIGN_RESOURCE");
    }

    @Test
    void blankTextsAreStoredAsNull() {
        Transaction t = Transaction.record(new NewTransaction(OWNER, vnd(1), DAY, "   ", " An ", false, null, null),
                cash, expense("Ăn uống"));

        assertThat(t.getNote()).isNull();
        assertThat(t.getWithPerson()).isEqualTo("An");
    }

    @Test
    void transferBetweenCurrenciesKeepsTheRate() {
        Wallet usd = wallet("USD", 0, "USD");

        TransferLegs legs = Transaction.transfer(OWNER, cash, usd, vnd(500_000), Money.of(20, "USD"), DAY, "Đổi",
                "key-1");

        assertThat(legs.out().getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(legs.in().getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(legs.out().getTransferId()).isEqualTo(legs.in().getTransferId());
        assertThat(legs.out().getCounterpartWalletId()).isEqualTo(usd.getId());
        assertThat(legs.out().getExchangeRate()).isEqualByComparingTo("0.00004");
        assertThat(legs.out().isExcludeFromReport()).isTrue();
        assertThat(legs.out().getIdempotencyKey()).isEqualTo("key-1");
        assertThat(legs.in().getIdempotencyKey()).isNull();
    }

    @Test
    void sameCurrencyTransfersMustReceiveTheAmountSent() {
        Wallet bank = wallet("Ngân hàng", 0);

        assertThatThrownBy(() -> Transaction.transfer(OWNER, cash, bank, vnd(100), vnd(90), DAY, null, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("TRANSFER_AMOUNT_MISMATCH");
        assertThatThrownBy(() -> Transaction.transfer(OWNER, cash, cash, vnd(100), vnd(100), DAY, null, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("SAME_WALLET");
    }

    @Test
    void adjustmentRecordsTheDifference() {
        Transaction up = Transaction.adjustment(OWNER, cash, vnd(1_200_000), DAY, null);
        Transaction down = Transaction.adjustment(OWNER, cash, vnd(900_000), DAY, null);

        assertThat(up.getType()).isEqualTo(TransactionType.ADJUSTMENT_IN);
        assertThat(up.getAmount()).isEqualTo(vnd(200_000));
        assertThat(down.getType()).isEqualTo(TransactionType.ADJUSTMENT_OUT);
        assertThat(down.signedAmount()).isEqualTo(vnd(-100_000));
        assertThatThrownBy(() -> Transaction.adjustment(OWNER, cash, vnd(1_000_000), DAY, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("NOTHING_TO_ADJUST");
    }

    @Test
    void onlyIncomeAndExpenseCanBeEdited() {
        Transaction adjustment = Transaction.adjustment(OWNER, cash, vnd(1), DAY, null);
        Transaction lunch = Transaction.record(spec(vnd(50_000)), cash, expense("Ăn uống"));
        Category salary = income("Lương");

        lunch.update(cash, salary, vnd(60_000), DAY.plusDays(1), "", null, true);

        assertThat(lunch.getType()).isEqualTo(TransactionType.INCOME);
        assertThat(lunch.getAmount()).isEqualTo(vnd(60_000));
        assertThat(lunch.isExcludeFromReport()).isTrue();
        assertThatThrownBy(adjustment::requireEditable)
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("TRANSACTION_NOT_EDITABLE");
    }

    private static NewTransaction spec(Money amount) {
        return new NewTransaction(OWNER, amount, DAY, null, null, false, null, null);
    }
}
