package com.financemanage.loan.domain;

import static com.financemanage.loan.LoanFixtures.loan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.loan.domain.model.Installment;
import com.financemanage.loan.domain.model.InstallmentStatus;
import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanStatus;
import com.financemanage.loan.domain.model.NewLoan;
import com.financemanage.loan.domain.model.Repayment;
import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.loan.domain.model.RepaymentType;
import com.financemanage.loan.domain.policy.EqualPrincipalScheduleGenerator;
import com.financemanage.loan.domain.policy.InterestFreeScheduleGenerator;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoanTest {

    private final EqualPrincipalScheduleGenerator equalPrincipal = new EqualPrincipalScheduleGenerator();

    private Loan newLoan() {
        return Loan.create(loan(RepaymentMethod.EQUAL_PRINCIPAL, 120_000_000, "12", 12), equalPrincipal);
    }

    private static Money vnd(long amount) {
        return Money.of(amount, "VND");
    }

    @Nested
    class Creation {

        @Test
        void buildsScheduleAndStartsActive() {
            Loan loan = newLoan();

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
            assertThat(loan.getInstallments()).hasSize(12);
            assertThat(loan.outstandingPrincipal()).isEqualTo(vnd(120_000_000));
            assertThat(loan.totalScheduledInterest()).isEqualTo(vnd(7_800_000));
        }

        @Test
        void validatesInput() {
            NewLoan base = loan(RepaymentMethod.EQUAL_PRINCIPAL, 1_000_000, "12", 12);
            NewLoan noTerm = new NewLoan(base.ownerId(), "A", base.direction(), base.type(), base.principal(),
                    base.annualRate(), 0, base.startDate(), base.repaymentMethod(), null, null);
            NewLoan noCounterparty = new NewLoan(base.ownerId(), " ", base.direction(), base.type(),
                    base.principal(), base.annualRate(), 12, base.startDate(), base.repaymentMethod(), null, null);

            assertThatThrownBy(() -> Loan.create(noTerm, equalPrincipal)).isInstanceOf(DomainException.class)
                    .extracting("code").isEqualTo("INVALID_TERM");
            assertThatThrownBy(() -> Loan.create(noCounterparty, equalPrincipal))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("COUNTERPARTY_REQUIRED");
        }

        @Test
        void interestFreeLoanStoresZeroRate() {
            Loan loan = Loan.create(loan(RepaymentMethod.INTEREST_FREE, 3_000_000, "10", 1),
                    new InterestFreeScheduleGenerator());

            assertThat(loan.currentRate(LocalDate.now()).isZero()).isTrue();
            assertThat(loan.getInstallments()).singleElement()
                    .satisfies(i -> assertThat(i.totalDue()).isEqualTo(vnd(3_000_000)));
        }
    }

    @Nested
    class Repayments {

        @Test
        void allocatesInterestBeforePrincipalOldestFirst() {
            Loan loan = newLoan();

            Repayment first = loan.recordRepayment(vnd(11_200_000), LocalDate.of(2025, 2, 15), null);
            Repayment partial = loan.recordRepayment(vnd(500_000), LocalDate.of(2025, 3, 1), null);

            assertThat(first.principalPortion()).isEqualTo(vnd(10_000_000));
            assertThat(first.interestPortion()).isEqualTo(vnd(1_200_000));
            assertThat(partial.interestPortion()).isEqualTo(vnd(500_000));
            assertThat(partial.principalPortion().isZero()).isTrue();
            Installment second = loan.getInstallments().get(1);
            assertThat(second.status(LocalDate.of(2025, 3, 1))).isEqualTo(InstallmentStatus.PARTIAL);
            assertThat(loan.outstandingPrincipal()).isEqualTo(vnd(110_000_000));
        }

        @Test
        void rejectsOverpayment() {
            Loan loan = newLoan();

            assertThatThrownBy(() -> loan.recordRepayment(vnd(200_000_000), LocalDate.of(2025, 2, 15), null))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("OVERPAYMENT");
        }

        @Test
        void sameIdempotencyKeyIsAppliedOnce() {
            Loan loan = newLoan();

            Repayment a = loan.recordRepayment(vnd(1_000_000), LocalDate.of(2025, 2, 15), "key-1");
            Repayment b = loan.recordRepayment(vnd(1_000_000), LocalDate.of(2025, 2, 15), "key-1");

            assertThat(b).isEqualTo(a);
            assertThat(loan.getRepayments()).hasSize(1);
        }

        @Test
        void payingEverythingClosesTheLoan() {
            Loan loan = newLoan();

            loan.recordRepayment(loan.totalRemaining(), LocalDate.of(2025, 2, 15), null);

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID_OFF);
            assertThat(loan.nextInstallment()).isEmpty();
            assertThatThrownBy(() -> loan.recordRepayment(vnd(1), LocalDate.of(2025, 2, 16), null))
                    .isInstanceOf(DomainException.class).extracting("code").isEqualTo("LOAN_NOT_ACTIVE");
        }

        @Test
        void detectsOverdueInstallments() {
            Loan loan = newLoan();

            assertThat(loan.overdueInstallments(LocalDate.of(2025, 2, 15))).isEmpty();
            assertThat(loan.overdueInstallments(LocalDate.of(2025, 3, 16))).hasSize(2);
            assertThat(loan.getInstallments().get(0).status(LocalDate.of(2025, 2, 16)))
                    .isEqualTo(InstallmentStatus.OVERDUE);
        }
    }

    @Nested
    class Prepayment {

        @Test
        void reducesBalanceChargesFeeAndRecalculatesRemainingInstallments() {
            Loan loan = newLoan();
            loan.recordRepayment(vnd(11_200_000), LocalDate.of(2025, 2, 15), null);

            Repayment prepayment = loan.prepay(vnd(55_000_000), LocalDate.of(2025, 2, 20), null, equalPrincipal);

            assertThat(prepayment.type()).isEqualTo(RepaymentType.PREPAYMENT);
            assertThat(prepayment.feePortion()).isEqualTo(vnd(550_000));
            assertThat(prepayment.amount()).isEqualTo(vnd(55_550_000));
            assertThat(loan.outstandingPrincipal()).isEqualTo(vnd(55_000_000));
            assertThat(loan.getInstallments()).hasSize(12);
            Installment second = loan.getInstallments().get(1);
            assertThat(second.getPeriodNo()).isEqualTo(2);
            assertThat(second.getDueDate()).isEqualTo(LocalDate.of(2025, 3, 15));
            assertThat(second.getPrincipalDue()).isEqualTo(vnd(5_000_000));
            assertThat(second.getInterestDue()).isEqualTo(vnd(550_000));
        }

        @Test
        void requiresDueInstallmentsToBePaidFirst() {
            Loan loan = newLoan();

            assertThatThrownBy(() -> loan.prepay(vnd(1_000_000), LocalDate.of(2025, 3, 1), null, equalPrincipal))
                    .isInstanceOf(DomainException.class).extracting("code")
                    .isEqualTo("SETTLE_DUE_INSTALLMENTS_FIRST");
        }

        @Test
        void fullPrepaymentPaysOffLoan() {
            Loan loan = newLoan();

            loan.prepay(vnd(120_000_000), LocalDate.of(2025, 1, 20), null, equalPrincipal);

            assertThat(loan.getStatus()).isEqualTo(LoanStatus.PAID_OFF);
            assertThat(loan.getInstallments()).isEmpty();
        }
    }

    @Nested
    class RateChange {

        @Test
        void appliesNewRateToPeriodsStartingAfterEffectiveDate() {
            Loan loan = newLoan();

            loan.changeRate(LocalDate.of(2025, 7, 15), Percentage.of("24"), equalPrincipal);

            assertThat(loan.getRatePeriods()).hasSize(2);
            // period 6 starts Jun 15 (old rate 1%/month on 70M), period 7 starts Jul 15 (2%/month on 60M)
            assertThat(loan.getInstallments().get(5).getInterestDue()).isEqualTo(vnd(700_000));
            assertThat(loan.getInstallments().get(6).getInterestDue()).isEqualTo(vnd(1_200_000));
            assertThat(loan.outstandingPrincipal()).isEqualTo(vnd(120_000_000));
        }

        @Test
        void rejectedForInterestFreeLoans() {
            InterestFreeScheduleGenerator generator = new InterestFreeScheduleGenerator();
            Loan loan = Loan.create(loan(RepaymentMethod.INTEREST_FREE, 3_000_000, "0", 3), generator);

            assertThatThrownBy(() -> loan.changeRate(LocalDate.of(2025, 2, 1), Percentage.of("5"), generator))
                    .isInstanceOf(DomainException.class);
        }
    }
}
