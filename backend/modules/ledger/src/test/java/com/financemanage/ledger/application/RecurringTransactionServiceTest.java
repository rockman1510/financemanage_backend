package com.financemanage.ledger.application;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.expense;
import static com.financemanage.ledger.LedgerFixtures.vnd;
import static com.financemanage.ledger.LedgerFixtures.wallet;
import static com.financemanage.ledger.application.InMemoryLedger.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.Frequency;
import com.financemanage.ledger.domain.model.RecurringTransaction;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.ledger.domain.model.Wallet.WalletChanges;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurringTransactionServiceTest {

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final RecurringTransactionService service = ledger.recurringService;
    private final Wallet bank = ledger.add(wallet("Ngân hàng", 50_000_000));
    private final Category rent = ledger.add(expense("Thuê nhà"));

    @Test
    void creatingARuleRecordsTheOccurrencesUpToToday() {
        RecurringTransaction rule = create(LocalDate.of(2025, 1, 1));

        assertThat(ledger.transactions.store.values()).extracting(Transaction::getOccurredOn).containsExactly(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1), LocalDate.of(2025, 3, 1), TODAY);
        assertThat(ledger.transactions.store.values()).allMatch(t -> rule.getId().equals(t.getRecurringId()));
        assertThat(rule.getNextOccurrence()).isEqualTo(LocalDate.of(2025, 5, 1));
        assertThat(ledger.balance(bank)).isEqualTo(vnd(30_000_000));
    }

    @Test
    void jobRunsAreIdempotent() {
        RecurringTransaction rule = create(TODAY.plusMonths(1));
        LocalDate due = TODAY.plusMonths(1);

        assertThat(service.dueRuleIds(due)).containsExactly(rule.getId());
        service.recordDue(rule.getId(), due);
        service.recordDue(rule.getId(), due);

        assertThat(ledger.transactions.store).hasSize(1);
        assertThat(service.dueRuleIds(due)).isEmpty();
    }

    @Test
    void ruleIsPausedWhenItsWalletIsArchived() {
        RecurringTransaction rule = create(TODAY.plusDays(1));
        ledger.walletService.update(bank.getId(), OWNER,
                new WalletChanges(null, null, null, true, null, null, null, null));

        service.recordDue(rule.getId(), TODAY.plusDays(1));

        assertThat(ledger.transactions.store).isEmpty();
        assertThat(service.get(rule.getId(), OWNER).isActive()).isFalse();
    }

    @Test
    void resumingDoesNotBackFill() {
        RecurringTransaction rule = create(TODAY.plusDays(1));
        service.update(rule.getId(), OWNER, new RecurringChanges(null, null, null, null, null, null, false));
        LocalDate later = TODAY.plusMonths(3);
        assertThat(service.dueRuleIds(later)).isEmpty();

        RecurringTransaction resumed = service.update(rule.getId(), OWNER,
                new RecurringChanges(null, null, new BigDecimal("6000000"), null, null, null, true));

        assertThat(resumed.isActive()).isTrue();
        assertThat(resumed.getNextOccurrence()).isAfterOrEqualTo(TODAY);
        assertThat(resumed.getAmount()).isEqualTo(vnd(6_000_000));
        assertThat(ledger.transactions.store).isEmpty();
    }

    private RecurringTransaction create(LocalDate start) {
        return service.create(OWNER, new RecurringInput(bank.getId(), rent.getId(), new BigDecimal("5000000"),
                "Tiền nhà", null, Frequency.MONTHLY, 1, start, null));
    }
}
