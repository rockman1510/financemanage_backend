package com.financemanage.deposit.domain;

import static com.financemanage.deposit.DepositFixtures.OPEN_DATE;
import static com.financemanage.deposit.DepositFixtures.demandDeposit;
import static com.financemanage.deposit.DepositFixtures.termDeposit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.deposit.domain.model.DepositStatus;
import com.financemanage.deposit.domain.model.DepositTransactionType;
import com.financemanage.deposit.domain.model.InterestPaymentMethod;
import com.financemanage.deposit.domain.model.MaturityOutcome;
import com.financemanage.deposit.domain.model.NewDeposit;
import com.financemanage.deposit.domain.model.RolloverPolicy;
import com.financemanage.deposit.domain.model.SettlementQuote;
import com.financemanage.deposit.domain.policy.InterestCalculator;
import com.financemanage.deposit.domain.policy.SimpleActual365InterestCalculator;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DepositTest {

    private final InterestCalculator calculator = new SimpleActual365InterestCalculator();

    @Nested
    class Opening {

        @Test
        void termDepositGetsMaturityDateAndOpenTransaction() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.NONE));

            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.ACTIVE);
            assertThat(deposit.getMaturityDate()).isEqualTo(LocalDate.of(2025, 7, 1));
            assertThat(deposit.getTransactions()).singleElement()
                    .satisfies(tx -> assertThat(tx.type()).isEqualTo(DepositTransactionType.OPEN));
        }

        @Test
        void demandDepositHasNoMaturityAndNeverRollsOver() {
            Deposit deposit = Deposit.open(demandDeposit());

            assertThat(deposit.getMaturityDate()).isNull();
            assertThat(deposit.getRolloverPolicy()).isEqualTo(RolloverPolicy.NONE);
            assertThat(deposit.expectedInterestAtMaturity(calculator)).isEmpty();
        }

        @Test
        void rejectsNonPositivePrincipal() {
            NewDeposit spec = termDeposit(RolloverPolicy.NONE);
            NewDeposit invalid = new NewDeposit(spec.ownerId(), spec.institutionName(), null, spec.type(),
                    Money.of(0, "VND"), spec.annualRate(), null, 6, OPEN_DATE, null, null, null, null);

            assertThatThrownBy(() -> Deposit.open(invalid)).isInstanceOf(DomainException.class)
                    .hasMessageContaining("Principal");
        }

        @Test
        void termDepositRequiresTerm() {
            NewDeposit spec = termDeposit(RolloverPolicy.NONE);
            NewDeposit invalid = new NewDeposit(spec.ownerId(), spec.institutionName(), null, spec.type(),
                    spec.principal(), spec.annualRate(), null, null, OPEN_DATE, null, null, null, null);

            assertThatThrownBy(() -> Deposit.open(invalid)).isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo("INVALID_TERM");
        }
    }

    @Nested
    class Interest {

        @Test
        void accruedInterestIsCappedAtMaturity() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.NONE));

            Money atMaturity = deposit.accruedInterest(calculator, LocalDate.of(2025, 7, 1));
            Money later = deposit.accruedInterest(calculator, LocalDate.of(2026, 1, 1));

            assertThat(atMaturity.amount()).isEqualByComparingTo("2975342");
            assertThat(later).isEqualTo(atMaturity);
            assertThat(deposit.expectedInterestAtMaturity(calculator)).contains(atMaturity);
        }
    }

    @Nested
    class Settlement {

        @Test
        void earlySettlementUsesEarlyWithdrawalRate() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.NONE));

            SettlementQuote quote = deposit.settlementQuote(calculator, LocalDate.of(2025, 4, 1));

            assertThat(quote.early()).isTrue();
            // 90 days: 0.5% -> 123,288 ; 6% -> 1,479,452
            assertThat(quote.interest().amount()).isEqualByComparingTo("123288");
            assertThat(quote.interestLostByEarlyWithdrawal().amount()).isEqualByComparingTo("1356164");
            assertThat(quote.total().amount()).isEqualByComparingTo("100123288");
        }

        @Test
        void settlementAtMaturityPaysFullInterestAndClosesDeposit() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.NONE));

            SettlementQuote quote = deposit.settle(calculator, LocalDate.of(2025, 7, 1));

            assertThat(quote.early()).isFalse();
            assertThat(quote.interest().amount()).isEqualByComparingTo("2975342");
            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.SETTLED);
            assertThat(deposit.getSettledAmount().amount()).isEqualByComparingTo("102975342");
            assertThatThrownBy(() -> deposit.settle(calculator, LocalDate.of(2025, 7, 2)))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("DEPOSIT_ALREADY_SETTLED");
        }

        @Test
        void rejectsSettlementBeforeOpenDate() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.NONE));

            assertThatThrownBy(() -> deposit.settlementQuote(calculator, OPEN_DATE.minusDays(1)))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    class Maturity {

        @Test
        void withoutRolloverDepositBecomesMatured() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.NONE));

            assertThat(deposit.processMaturity(calculator, LocalDate.of(2025, 6, 30))).isEmpty();
            List<MaturityOutcome> outcomes = deposit.processMaturity(calculator, LocalDate.of(2025, 7, 1));

            assertThat(outcomes).singleElement().satisfies(o -> assertThat(o.rolledOver()).isFalse());
            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.MATURED);
        }

        @Test
        void principalAndInterestRolloverCapitalizesInterest() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.PRINCIPAL_AND_INTEREST));

            deposit.processMaturity(calculator, LocalDate.of(2025, 7, 1));

            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.ACTIVE);
            assertThat(deposit.getPrincipal().amount()).isEqualByComparingTo("102975342");
            assertThat(deposit.getOpenDate()).isEqualTo(LocalDate.of(2025, 7, 1));
            assertThat(deposit.getMaturityDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        }

        @Test
        void principalOnlyRolloverPaysInterestOutAndCatchesUpMissedTerms() {
            Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.PRINCIPAL_ONLY));

            List<MaturityOutcome> outcomes = deposit.processMaturity(calculator, LocalDate.of(2026, 2, 1));

            assertThat(outcomes).hasSize(2).allMatch(MaturityOutcome::rolledOver);
            assertThat(deposit.getPrincipal().amount()).isEqualByComparingTo("100000000");
            assertThat(deposit.getMaturityDate()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(deposit.getTransactions())
                    .filteredOn(tx -> tx.type() == DepositTransactionType.INTEREST_PAID).hasSize(2);
        }

        @Test
        void monthlyInterestIsNotCapitalizedEvenWithPrincipalAndInterestRollover() {
            NewDeposit base = termDeposit(RolloverPolicy.PRINCIPAL_AND_INTEREST);
            NewDeposit monthly = new NewDeposit(base.ownerId(), base.institutionName(), null, base.type(),
                    base.principal(), base.annualRate(), null, 6, OPEN_DATE, InterestPaymentMethod.MONTHLY,
                    RolloverPolicy.PRINCIPAL_AND_INTEREST, null, null);
            Deposit deposit = Deposit.open(monthly);

            deposit.processMaturity(calculator, LocalDate.of(2025, 7, 1));

            assertThat(deposit.getPrincipal().amount()).isEqualByComparingTo("100000000");
        }
    }

    @Test
    void updateDetailsRejectsBlankInstitution() {
        Deposit deposit = Deposit.open(termDeposit(RolloverPolicy.NONE));

        deposit.updateDetails("BIDV", "999", "note");
        assertThat(deposit.getInstitutionName()).isEqualTo("BIDV");
        assertThat(deposit.getNote()).isEqualTo("note");
        assertThatThrownBy(() -> deposit.updateDetails(" ", null, null)).isInstanceOf(DomainException.class);
    }
}
