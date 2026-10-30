package com.financemanage.ledger.application;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.expense;
import static com.financemanage.ledger.LedgerFixtures.income;
import static com.financemanage.ledger.LedgerFixtures.vnd;
import static com.financemanage.ledger.LedgerFixtures.wallet;
import static com.financemanage.ledger.application.InMemoryLedger.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.api.LedgerEvents.TransactionDeleted;
import com.financemanage.ledger.api.LedgerEvents.TransactionRecorded;
import com.financemanage.ledger.api.LedgerEvents.TransactionUpdated;
import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.Transaction.TransferLegs;
import com.financemanage.ledger.domain.model.TransactionFilter;
import com.financemanage.ledger.domain.model.TransactionTotals;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionServiceTest {

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final TransactionService service = ledger.transactionService;
    private final Wallet cash = ledger.add(wallet("Tiền mặt", 1_000_000));
    private final Wallet bank = ledger.add(wallet("Ngân hàng", 5_000_000));
    private final Category food = ledger.add(expense("Ăn uống"));
    private final Category salary = ledger.add(income("Lương"));

    @Test
    void recordingUpdatesTheBalanceAndPublishesASnapshot() {
        Transaction lunch = record(cash, food, 50_000, null);

        assertThat(ledger.balance(cash)).isEqualTo(vnd(950_000));
        assertThat(lunch.getOccurredOn()).isEqualTo(TODAY);
        assertThat(ledger.events).singleElement().isInstanceOfSatisfying(TransactionRecorded.class, e -> {
            assertThat(e.transaction().type()).isEqualTo("EXPENSE");
            assertThat(e.transaction().signedAmount()).isEqualByComparingTo("-50000");
            assertThat(e.transaction().currency()).isEqualTo("VND");
        });
    }

    @Test
    void sameIdempotencyKeyRecordsOnce() {
        Transaction first = record(cash, food, 50_000, "k-1");
        Transaction replay = record(cash, food, 50_000, "k-1");

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(ledger.balance(cash)).isEqualTo(vnd(950_000));
    }

    @Test
    void aKeyUsedForATransactionCannotBeReusedForATransferAndViceVersa() {
        record(cash, food, 50_000, "k-2");
        service.transfer(OWNER, new TransferInput(bank.getId(), cash.getId(), BigDecimal.TEN, null, null, null),
                "t-2");

        assertThatThrownBy(() -> service.transfer(OWNER, new TransferInput(bank.getId(), cash.getId(),
                BigDecimal.TEN, null, null, null), "k-2"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThatThrownBy(() -> record(cash, food, 50_000, "t-2"))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(ledger.transactions.store).hasSize(3);
    }

    @Test
    void movingATransactionToAnotherWalletMovesTheMoney() {
        Transaction lunch = record(cash, food, 50_000, null);

        Transaction moved = service.update(lunch.getId(), OWNER, new TransactionChanges(bank.getId(), salary.getId(),
                new BigDecimal("80000"), null, "Thưởng", null, null));

        assertThat(moved.getWalletId()).isEqualTo(bank.getId());
        assertThat(ledger.balance(cash)).isEqualTo(vnd(1_000_000));
        assertThat(ledger.balance(bank)).isEqualTo(vnd(5_080_000));
        assertThat(ledger.events).last().isInstanceOfSatisfying(TransactionUpdated.class, e -> {
            assertThat(e.before().signedAmount()).isEqualByComparingTo("-50000");
            assertThat(e.after().signedAmount()).isEqualByComparingTo("80000");
        });
    }

    @Test
    void movingToAWalletOfAnotherCurrencyNeedsANewAmount() {
        Wallet usd = ledger.add(wallet("USD", 0, "USD"));
        Transaction lunch = record(cash, food, 50_000, null);

        assertThatThrownBy(() -> service.update(lunch.getId(), OWNER,
                new TransactionChanges(usd.getId(), null, null, null, null, null, null)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CURRENCY_MISMATCH");
        service.update(lunch.getId(), OWNER, new TransactionChanges(usd.getId(), null, new BigDecimal("2.5"), null,
                null, null, null));
        assertThat(ledger.balance(usd)).isEqualTo(Money.of("-2.5", "USD"));
        assertThat(ledger.balance(cash)).isEqualTo(vnd(1_000_000));
    }

    @Test
    void deletingRevertsTheBalanceAndRemovesAttachments() {
        Transaction lunch = record(cash, food, 50_000, null);
        ledger.attachmentService.upload(OWNER, lunch.getId(), "bill.jpg", "image/jpeg", 3,
                new ByteArrayInputStream(new byte[] {1, 2, 3}));

        service.delete(lunch.getId(), OWNER);

        assertThat(ledger.balance(cash)).isEqualTo(vnd(1_000_000));
        assertThat(ledger.attachments.store).isEmpty();
        assertThat(ledger.storage.files).isEmpty();
        assertThat(ledger.events).last().isInstanceOf(TransactionDeleted.class);
    }

    @Test
    void transferMovesMoneyBetweenWalletsAndIsIdempotent() {
        TransferLegs legs = service.transfer(OWNER, new TransferInput(bank.getId(), cash.getId(),
                new BigDecimal("2000000"), null, null, "Rút tiền"), "t-1");
        TransferLegs replay = service.transfer(OWNER, new TransferInput(bank.getId(), cash.getId(),
                new BigDecimal("2000000"), null, null, "Rút tiền"), "t-1");

        assertThat(replay.out().getId()).isEqualTo(legs.out().getId());
        assertThat(ledger.balance(bank)).isEqualTo(vnd(3_000_000));
        assertThat(ledger.balance(cash)).isEqualTo(vnd(3_000_000));

        service.delete(legs.in().getId(), OWNER); // deleting one leg deletes the whole transfer
        assertThat(ledger.transactions.store).isEmpty();
        assertThat(ledger.balance(bank)).isEqualTo(vnd(5_000_000));
        assertThat(ledger.balance(cash)).isEqualTo(vnd(1_000_000));
    }

    @Test
    void transferBetweenCurrenciesUsesTheRateWhenNoAmountIsGiven() {
        Wallet usd = ledger.add(wallet("USD", 100, "USD"));

        TransferLegs legs = service.transfer(OWNER, new TransferInput(usd.getId(), cash.getId(),
                new BigDecimal("10"), null, null, null), null);

        assertThat(legs.in().getAmount()).isEqualTo(vnd(250_000));
        assertThat(ledger.balance(usd)).isEqualTo(Money.of(90, "USD"));
        assertThat(ledger.balance(cash)).isEqualTo(vnd(1_250_000));

        Wallet eur = ledger.add(wallet("EUR", 0, "EUR"));
        assertThatThrownBy(() -> service.transfer(OWNER, new TransferInput(cash.getId(), eur.getId(),
                new BigDecimal("1000"), null, null, null), null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("EXCHANGE_RATE_MISSING");
        service.transfer(OWNER, new TransferInput(cash.getId(), eur.getId(), new BigDecimal("270000"),
                new BigDecimal("10"), null, null), null);
        assertThat(ledger.balance(eur)).isEqualTo(Money.of(10, "EUR"));
    }

    @Test
    void adjustmentsAndTransfersCannotBeEdited() {
        Transaction adjustment = service.adjustBalance(cash.getId(), OWNER, new BigDecimal("1200000"), null, null);

        assertThat(ledger.balance(cash)).isEqualTo(vnd(1_200_000));
        assertThatThrownBy(() -> service.update(adjustment.getId(), OWNER,
                new TransactionChanges(null, null, BigDecimal.ONE, null, null, null, null)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("TRANSACTION_NOT_EDITABLE");
    }

    @Test
    void filteringByAParentCategoryIncludesItsSubCategories() {
        Category coffee = ledger.add(Category.create(OWNER, "Cà phê", CategoryType.EXPENSE, food, null));
        record(cash, food, 100_000, null);
        record(cash, coffee, 30_000, null);
        record(cash, salary, 10_000_000, null);
        service.transfer(OWNER, new TransferInput(cash.getId(), bank.getId(), new BigDecimal("1000"), null, null,
                null), null);

        TransactionFilter byFood = new TransactionFilter(OWNER, null, Set.of(food.getId()), null, null, null, null,
                null, null);
        assertThat(service.search(byFood, 0, 10).total()).isEqualTo(2);

        List<TransactionTotals> totals = service.totals(new TransactionFilter(OWNER, null, null, null, null, null,
                null, null, null));
        assertThat(totals).singleElement().satisfies(t -> {
            assertThat(t.income()).isEqualTo(vnd(10_000_000));
            assertThat(t.expense()).isEqualTo(vnd(130_000)); // the transfer is not an expense
        });
    }

    @Test
    void otherUsersResourcesAreNotFound() {
        Transaction lunch = record(cash, food, 50_000, null);

        assertThatThrownBy(() -> service.get(lunch.getId(), UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    private Transaction record(Wallet wallet, Category category, long amount, String key) {
        return service.record(OWNER, new TransactionInput(wallet.getId(), category.getId(), BigDecimal.valueOf(amount),
                null, null, null, false), key);
    }
}
