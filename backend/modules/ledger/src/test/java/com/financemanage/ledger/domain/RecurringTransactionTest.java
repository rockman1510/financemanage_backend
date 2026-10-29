package com.financemanage.ledger.domain;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.expense;
import static com.financemanage.ledger.LedgerFixtures.vnd;
import static com.financemanage.ledger.LedgerFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.domain.model.Frequency;
import com.financemanage.ledger.domain.model.RecurringTransaction;
import com.financemanage.ledger.domain.model.RecurringTransaction.NewRecurringTransaction;
import com.financemanage.shared.domain.DomainException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurringTransactionTest {

    @Test
    void monthlyRuleOnThe31stFollowsShortMonthsWithoutDrifting() {
        RecurringTransaction rule = rule(Frequency.MONTHLY, 1, LocalDate.of(2025, 1, 31), null);

        assertThat(occurrences(rule, 4)).containsExactly(LocalDate.of(2025, 1, 31), LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 30));
    }

    @Test
    void intervalAndEndDateLimitTheOccurrences() {
        RecurringTransaction rule = rule(Frequency.WEEKLY, 2, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 29));

        assertThat(occurrences(rule, 10)).containsExactly(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 15),
                LocalDate.of(2025, 4, 29));
        assertThat(rule.isActive()).isFalse();
        assertThat(rule.getNextOccurrence()).isNull();
    }

    @Test
    void resumingSkipsTheOccurrencesMissedWhilePaused() {
        RecurringTransaction rule = rule(Frequency.DAILY, 1, LocalDate.of(2025, 4, 1), null);
        rule.pause();
        assertThat(rule.isDue(LocalDate.of(2025, 4, 10))).isFalse();

        rule.resume(LocalDate.of(2025, 4, 10));

        assertThat(rule.getNextOccurrence()).isEqualTo(LocalDate.of(2025, 4, 10));
        assertThat(rule.isDue(LocalDate.of(2025, 4, 10))).isTrue();
    }

    @Test
    void validatesTheSchedule() {
        assertThatThrownBy(() -> rule(Frequency.DAILY, 0, LocalDate.of(2025, 4, 1), null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_INTERVAL");
        assertThatThrownBy(() -> rule(Frequency.DAILY, 1, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 3, 1)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_END_DATE");
    }

    private static RecurringTransaction rule(Frequency frequency, int interval, LocalDate start, LocalDate end) {
        return RecurringTransaction.create(new NewRecurringTransaction(OWNER, vnd(5_000_000), "Tiền nhà", null,
                frequency, interval, start, end), wallet("Ví", 0), expense("Thuê nhà"));
    }

    private static List<LocalDate> occurrences(RecurringTransaction rule, int max) {
        List<LocalDate> dates = new ArrayList<>();
        while (rule.isActive() && dates.size() < max) {
            dates.add(rule.getNextOccurrence());
            rule.markGenerated();
        }
        return dates;
    }
}
