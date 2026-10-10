package com.financemanage.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.loan.domain.model.Installment;
import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.loan.domain.policy.AnnuityScheduleGenerator;
import com.financemanage.loan.domain.policy.BulletScheduleGenerator;
import com.financemanage.loan.domain.policy.EqualPrincipalScheduleGenerator;
import com.financemanage.loan.domain.policy.InterestFreeScheduleGenerator;
import com.financemanage.loan.domain.policy.RepaymentScheduleGenerator;
import com.financemanage.loan.domain.policy.ScheduleGeneratorRegistry;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleGeneratorTest {

    private static final LocalDate START = LocalDate.of(2025, 1, 15);

    private static List<Installment> generate(RepaymentScheduleGenerator generator, long principal, String rate,
                                              int months) {
        return generator.generate(Money.of(principal, "VND"), months, START, 1, d -> Percentage.of(rate));
    }

    private static BigDecimal sum(List<Installment> installments, boolean principal) {
        return installments.stream()
                .map(i -> principal ? i.getPrincipalDue().amount() : i.getInterestDue().amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void equalPrincipalRepaysSamePrincipalWithDecliningInterest() {
        List<Installment> schedule = generate(new EqualPrincipalScheduleGenerator(), 120_000_000, "12", 12);

        assertThat(schedule).hasSize(12);
        assertThat(schedule).allSatisfy(i -> assertThat(i.getPrincipalDue().amount()).isEqualByComparingTo("10000000"));
        assertThat(schedule.get(0).getInterestDue().amount()).isEqualByComparingTo("1200000");
        assertThat(schedule.get(11).getInterestDue().amount()).isEqualByComparingTo("100000");
        assertThat(sum(schedule, false)).isEqualByComparingTo("7800000");
        assertThat(schedule.get(0).getDueDate()).isEqualTo(LocalDate.of(2025, 2, 15));
        assertThat(schedule.get(11).getDueDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void annuityKeepsTotalPaymentConstant() {
        List<Installment> schedule = generate(new AnnuityScheduleGenerator(), 100_000_000, "12", 12);

        // payment = 100,000,000 × 0.01 / (1 − 1.01^−12) ≈ 8,884,879
        assertThat(schedule.get(0).getInterestDue().amount()).isEqualByComparingTo("1000000");
        assertThat(schedule).allSatisfy(i -> assertThat(i.totalDue().amount().doubleValue())
                .isCloseTo(8_884_879, org.assertj.core.data.Offset.offset(5.0)));
        assertThat(sum(schedule, true)).isEqualByComparingTo("100000000");
    }

    @Test
    void annuityWithZeroRateSplitsPrincipalEvenly() {
        List<Installment> schedule = generate(new AnnuityScheduleGenerator(), 12_000_000, "0", 4);

        assertThat(schedule).allSatisfy(i -> {
            assertThat(i.getPrincipalDue().amount()).isEqualByComparingTo("3000000");
            assertThat(i.getInterestDue().isZero()).isTrue();
        });
    }

    @Test
    void bulletPaysInterestOnlyUntilLastInstallment() {
        List<Installment> schedule = generate(new BulletScheduleGenerator(), 12_000_000, "12", 3);

        assertThat(schedule).extracting(i -> i.getPrincipalDue().amount().longValue())
                .containsExactly(0L, 0L, 12_000_000L);
        assertThat(schedule).allSatisfy(i -> assertThat(i.getInterestDue().amount()).isEqualByComparingTo("120000"));
    }

    @Test
    void interestFreeIgnoresRate() {
        List<Installment> schedule = generate(new InterestFreeScheduleGenerator(), 10_000_000, "12", 3);

        assertThat(sum(schedule, false)).isEqualByComparingTo("0");
        assertThat(schedule).extracting(i -> i.getPrincipalDue().amount().longValue())
                .containsExactly(3_333_333L, 3_333_333L, 3_333_334L);
    }

    @Test
    void usesRateApplicableAtEachPeriodStart() {
        List<Installment> schedule = new EqualPrincipalScheduleGenerator().generate(Money.of(12_000_000, "VND"), 3,
                START, 1, d -> d.isBefore(LocalDate.of(2025, 3, 15)) ? Percentage.of("12") : Percentage.of("24"));

        assertThat(schedule).extracting(i -> i.getInterestDue().amount().longValue())
                .containsExactly(120_000L, 80_000L, 80_000L);
    }

    @Test
    void registryRejectsUnknownMethod() {
        var registry = new ScheduleGeneratorRegistry(List.of(new BulletScheduleGenerator()));

        assertThat(registry.get(RepaymentMethod.BULLET)).isInstanceOf(BulletScheduleGenerator.class);
        assertThatThrownBy(() -> registry.get(RepaymentMethod.ANNUITY)).isInstanceOf(DomainException.class);
    }
}
