package com.financemanage.bill.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.bill.domain.model.Bill;
import com.financemanage.bill.domain.model.Bill.NewBill;
import com.financemanage.bill.domain.model.BillFrequency;
import com.financemanage.bill.domain.model.BillPayment;
import com.financemanage.bill.domain.model.PaymentStatus;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillTest {

    private static final Instant NOW = Instant.parse("2025-04-01T00:00:00Z");

    @Test
    void payingMovesToTheNextDueDateWithoutDrifting() {
        Bill rent = bill(LocalDate.of(2025, 1, 31), null);

        rent.pay(vnd(5_000_000), rent.getWalletId(), LocalDate.of(2025, 1, 30), NOW);
        assertThat(rent.getNextDueDate()).isEqualTo(LocalDate.of(2025, 2, 28));
        rent.skip(NOW);
        assertThat(rent.getNextDueDate()).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(rent.getPayments()).extracting(BillPayment::getStatus)
                .containsExactly(PaymentStatus.PENDING, PaymentStatus.SKIPPED);
    }

    @Test
    void endsAfterTheEndDate() {
        Bill bill = bill(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 2, 10));

        bill.pay(vnd(1), bill.getWalletId(), LocalDate.of(2025, 1, 5), NOW);
        bill.pay(vnd(1), bill.getWalletId(), LocalDate.of(2025, 2, 5), NOW);

        assertThat(bill.getNextDueDate()).isNull();
        assertThat(bill.isActive()).isFalse();
        assertThatThrownBy(() -> bill.skip(NOW))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("BILL_FINISHED");
    }

    @Test
    void aRejectedLatestPaymentMakesItsDueDateDueAgain() {
        Bill bill = bill(LocalDate.of(2025, 1, 5), null);
        BillPayment january = bill.pay(vnd(1), bill.getWalletId(), LocalDate.of(2025, 1, 5), NOW);
        BillPayment february = bill.pay(vnd(1), bill.getWalletId(), LocalDate.of(2025, 2, 5), NOW);

        bill.paymentRecorded(january.getId(), UUID.randomUUID());
        bill.paymentFailed(february.getId(), "WALLET_ARCHIVED");

        assertThat(bill.getNextDueDate()).isEqualTo(LocalDate.of(2025, 2, 5));
        assertThat(bill.getPayments()).extracting(BillPayment::getStatus)
                .containsExactly(PaymentStatus.RECORDED, PaymentStatus.FAILED);
        assertThat(bill.isOverdue(LocalDate.of(2025, 2, 6))).isTrue();
        assertThat(bill.daysUntilDue(LocalDate.of(2025, 2, 1))).isEqualTo(4);
    }

    @Test
    void anOlderRejectedPaymentKeepsTheSchedule() {
        Bill bill = bill(LocalDate.of(2025, 1, 5), null);
        BillPayment january = bill.pay(vnd(1), bill.getWalletId(), LocalDate.of(2025, 1, 5), NOW);
        bill.pay(vnd(1), bill.getWalletId(), LocalDate.of(2025, 2, 5), NOW);

        bill.paymentFailed(january.getId(), "WALLET_ARCHIVED");

        assertThat(bill.getNextDueDate()).isEqualTo(LocalDate.of(2025, 3, 5));
    }

    private static Bill bill(LocalDate first, LocalDate end) {
        return Bill.create(new NewBill(UUID.randomUUID(), "Tiền nhà", vnd(5_000_000), UUID.randomUUID(),
                UUID.randomUUID(), BillFrequency.MONTHLY, 1, first, end, 3, null));
    }

    private static Money vnd(long amount) {
        return Money.of(amount, "VND");
    }
}
