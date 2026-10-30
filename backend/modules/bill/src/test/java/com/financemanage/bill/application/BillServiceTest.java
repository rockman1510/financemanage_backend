package com.financemanage.bill.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.bill.api.BillEvents.BillDueSoon;
import com.financemanage.bill.api.BillEvents.BillOverdue;
import com.financemanage.bill.api.BillEvents.BillPaid;
import com.financemanage.bill.api.BillEvents.BillPaymentFailed;
import com.financemanage.bill.domain.BillRepository;
import com.financemanage.bill.domain.model.Bill;
import com.financemanage.bill.domain.model.BillFrequency;
import com.financemanage.bill.domain.model.BillPayment;
import com.financemanage.bill.domain.model.PaymentStatus;
import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 4, 1);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();
    private static final UUID OLD_WALLET = UUID.randomUUID();
    private static final UUID RENT = UUID.randomUUID();
    private static final UUID SALARY = UUID.randomUUID();

    private final List<Object> events = new ArrayList<>();
    private final InMemoryBills repository = new InMemoryBills();
    private final BillService service = new BillService(repository, new FakeLedger(), events::add,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void payingPublishesALedgerCommandAndTheAnswerRecordsIt() {
        Bill bill = create(TODAY.plusDays(3));

        BillPayment payment = service.pay(bill.getId(), OWNER, new PayBillInput(null, null, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(events).singleElement().isInstanceOfSatisfying(RecordExternalTransaction.class, c -> {
            assertThat(c.requestId()).isEqualTo(payment.getId());
            assertThat(c.source()).isEqualTo("BILL");
            assertThat(c.inflow()).isFalse();
            assertThat(c.categoryId()).isEqualTo(RENT);
            assertThat(c.amount()).isEqualByComparingTo("5000000");
            assertThat(c.occurredOn()).isEqualTo(TODAY);
        });

        UUID transactionId = UUID.randomUUID();
        service.paymentRecorded(payment.getId(), transactionId);

        assertThat(service.get(bill.getId(), OWNER).getPayments()).singleElement()
                .satisfies(p -> assertThat(p.getTransactionId()).isEqualTo(transactionId));
        assertThat(events).last().isInstanceOf(BillPaid.class);
    }

    @Test
    void aRejectedPaymentMakesTheBillDueAgain() {
        Bill bill = create(TODAY);
        BillPayment payment = service.pay(bill.getId(), OWNER, new PayBillInput(new BigDecimal("4800000"), null,
                null));
        assertThat(service.get(bill.getId(), OWNER).getNextDueDate()).isEqualTo(TODAY.plusMonths(1));

        service.paymentRejected(payment.getId(), "WALLET_ARCHIVED: archived");

        assertThat(service.get(bill.getId(), OWNER).getNextDueDate()).isEqualTo(TODAY);
        assertThat(events).last().isInstanceOf(BillPaymentFailed.class);
    }

    @Test
    void validatesWalletAndCategory() {
        assertThatThrownBy(() -> service.create(OWNER, input(SALARY, CASH, TODAY)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_CATEGORY");
        assertThatThrownBy(() -> service.create(OWNER, input(RENT, OLD_WALLET, TODAY)))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("WALLET_ARCHIVED");
    }

    @Test
    void remindsBeforeTheDueDateAndAlertsTheDayAfter() {
        create(TODAY.plusDays(3));
        create(TODAY.minusDays(1));
        create(TODAY.plusDays(10));

        assertThat(service.remind(TODAY)).isEqualTo(2);
        assertThat(events).hasSize(2).anyMatch(e -> e instanceof BillDueSoon d && d.daysLeft() == 3)
                .anyMatch(e -> e instanceof BillOverdue);
        assertThat(service.upcoming(OWNER, 7)).hasSize(2);
    }

    private Bill create(LocalDate firstDue) {
        return service.create(OWNER, input(RENT, CASH, firstDue));
    }

    private static BillInput input(UUID category, UUID wallet, LocalDate firstDue) {
        return new BillInput("Tiền nhà", new BigDecimal("5000000"), wallet, category, BillFrequency.MONTHLY, null,
                firstDue, null, 3, null);
    }

    private static class FakeLedger implements LedgerQueryApi {
        @Override
        public List<WalletBalance> walletBalances(UUID ownerId) {
            return List.of(new WalletBalance(CASH, "Tiền mặt", "CASH", Money.of(0, "VND"), false, false),
                    new WalletBalance(OLD_WALLET, "Ví cũ", "CASH", Money.of(0, "VND"), false, true));
        }

        @Override
        public List<CategoryView> categories(UUID ownerId) {
            return List.of(new CategoryView(RENT, "Thuê nhà", "EXPENSE", null),
                    new CategoryView(SALARY, "Lương", "INCOME", null));
        }

        @Override
        public List<Money> expenseTotals(UUID ownerId, Set<UUID> categoryIds, UUID walletId, LocalDate from,
                                         LocalDate to) {
            return List.of();
        }
    }

    private static class InMemoryBills implements BillRepository {
        final Map<UUID, Bill> store = new LinkedHashMap<>();

        @Override
        public Bill save(Bill bill) {
            store.put(bill.getId(), bill);
            return bill;
        }

        @Override
        public Optional<Bill> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(b -> b.getOwnerId().equals(ownerId));
        }

        @Override
        public Optional<Bill> findByPayment(UUID paymentId) {
            return store.values().stream()
                    .filter(b -> b.getPayments().stream().anyMatch(p -> p.getId().equals(paymentId))).findFirst();
        }

        @Override
        public List<Bill> findByOwner(UUID ownerId) {
            return store.values().stream().filter(b -> b.getOwnerId().equals(ownerId)).toList();
        }

        @Override
        public List<Bill> findActiveDueBetween(LocalDate from, LocalDate to) {
            return store.values().stream().filter(b -> b.isActive() && b.getNextDueDate() != null
                    && !b.getNextDueDate().isBefore(from) && !b.getNextDueDate().isAfter(to)).toList();
        }

        @Override
        public void delete(Bill bill) {
            store.remove(bill.getId());
        }

        @Override
        public void deleteByOwner(UUID ownerId) {
            store.values().removeIf(b -> b.getOwnerId().equals(ownerId));
        }
    }
}
