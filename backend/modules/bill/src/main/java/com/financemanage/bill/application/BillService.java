package com.financemanage.bill.application;

import com.financemanage.bill.api.BillEvents.BillDueSoon;
import com.financemanage.bill.api.BillEvents.BillOverdue;
import com.financemanage.bill.api.BillEvents.BillPaid;
import com.financemanage.bill.api.BillEvents.BillPaymentFailed;
import com.financemanage.bill.domain.BillRepository;
import com.financemanage.bill.domain.model.Bill;
import com.financemanage.bill.domain.model.Bill.NewBill;
import com.financemanage.bill.domain.model.BillPayment;
import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bill use cases. Paying is a two-step saga: the payment is saved as PENDING and a {@link RecordExternalTransaction}
 * command is published (outbox); the ledger's answer marks it RECORDED, or FAILED and the due date comes back.
 */
@Service
@Transactional
public class BillService {

    static final String SOURCE = "BILL";
    private static final int MAX_REMIND_DAYS = 30;

    private final BillRepository bills;
    private final LedgerQueryApi ledger;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BillService(BillRepository bills, LedgerQueryApi ledger, ApplicationEventPublisher events, Clock clock) {
        this.bills = bills;
        this.ledger = ledger;
        this.events = events;
        this.clock = clock;
    }

    public Bill create(UUID ownerId, BillInput input) {
        WalletBalance wallet = wallet(ownerId, input.walletId());
        requireExpenseCategory(ownerId, input.categoryId());
        return bills.save(Bill.create(new NewBill(ownerId, input.name(), money(input.amount(), wallet),
                input.walletId(), input.categoryId(), input.frequency(),
                input.interval() == null ? 1 : input.interval(), input.firstDueDate(), input.endDate(),
                input.remindDaysBefore(), input.note())));
    }

    /** Bills by next due date (ended ones last). */
    @Transactional(readOnly = true)
    public List<Bill> list(UUID ownerId) {
        return bills.findByOwner(ownerId).stream()
                .sorted(Comparator.comparing(Bill::getNextDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Active bills overdue or due within {@code days} days. */
    @Transactional(readOnly = true)
    public List<Bill> upcoming(UUID ownerId, int days) {
        LocalDate limit = today().plusDays(days);
        return list(ownerId).stream()
                .filter(b -> b.isActive() && b.getNextDueDate() != null && !b.getNextDueDate().isAfter(limit))
                .toList();
    }

    @Transactional(readOnly = true)
    public Bill get(UUID id, UUID ownerId) {
        return load(id, ownerId);
    }

    public Bill update(UUID id, UUID ownerId, BillChanges changes) {
        Bill bill = load(id, ownerId);
        UUID walletId = changes.walletId() != null ? changes.walletId() : bill.getWalletId();
        WalletBalance wallet = wallet(ownerId, walletId);
        if (changes.categoryId() != null) {
            requireExpenseCategory(ownerId, changes.categoryId());
        }
        Money amount = changes.amount() != null ? money(changes.amount(), wallet) : null;
        if (amount == null && !wallet.balance().currency().equals(bill.getAmount().currency())) {
            throw new DomainException("CURRENCY_MISMATCH", "Give the amount in the new wallet's currency");
        }
        bill.update(changes.name(), amount, changes.walletId(), changes.categoryId(), changes.endDate(),
                changes.remindDaysBefore(), changes.note());
        if (Boolean.FALSE.equals(changes.active())) {
            bill.pause();
        } else if (Boolean.TRUE.equals(changes.active())) {
            bill.resume();
        }
        return bills.save(bill);
    }

    public void delete(UUID id, UUID ownerId) {
        bills.delete(load(id, ownerId));
    }

    /** Pays the current due date; the expense appears in the ledger a moment later (payment PENDING → RECORDED). */
    public BillPayment pay(UUID id, UUID ownerId, PayBillInput input) {
        Bill bill = load(id, ownerId);
        UUID walletId = input.walletId() != null ? input.walletId() : bill.getWalletId();
        WalletBalance wallet = wallet(ownerId, walletId);
        Money amount = input.amount() != null ? money(input.amount(), wallet) : bill.getAmount();
        if (!amount.currency().equals(wallet.balance().currency())) {
            throw new DomainException("CURRENCY_MISMATCH", "The wallet holds " + wallet.balance().currencyCode());
        }
        LocalDate paidOn = input.paidOn() != null ? input.paidOn() : today();
        BillPayment payment = bill.pay(amount, walletId, paidOn, clock.instant());
        bills.save(bill);
        events.publishEvent(new RecordExternalTransaction(payment.getId(), SOURCE, payment.getId(), ownerId,
                walletId, false, bill.getCategoryId(), amount.amount(), amount.currencyCode(), paidOn,
                "Hoá đơn: " + bill.getName()));
        return payment;
    }

    public BillPayment skip(UUID id, UUID ownerId) {
        Bill bill = load(id, ownerId);
        BillPayment skipped = bill.skip(clock.instant());
        bills.save(bill);
        return skipped;
    }

    public void paymentRecorded(UUID paymentId, UUID transactionId) {
        bills.findByPayment(paymentId).ifPresent(bill -> {
            bill.paymentRecorded(paymentId, transactionId);
            bills.save(bill);
            BillPayment payment = bill.getPayments().stream().filter(p -> p.getId().equals(paymentId)).findFirst()
                    .orElseThrow();
            events.publishEvent(new BillPaid(bill.getId(), bill.getOwnerId(), paymentId, bill.getName(),
                    payment.getAmount().roundedAmount(), payment.getAmount().currencyCode(), payment.getPaidOn(),
                    transactionId));
        });
    }

    public void paymentRejected(UUID paymentId, String reason) {
        bills.findByPayment(paymentId).ifPresent(bill -> {
            bill.paymentFailed(paymentId, reason);
            bills.save(bill);
            events.publishEvent(new BillPaymentFailed(bill.getId(), bill.getOwnerId(), paymentId, bill.getName(),
                    reason));
        });
    }

    /** Publishes reminders for bills due in their reminder window and alerts for bills that became overdue. */
    public int remind(LocalDate today) {
        int sent = 0;
        for (Bill bill : bills.findActiveDueBetween(today.minusDays(1), today.plusDays(MAX_REMIND_DAYS))) {
            LocalDate due = bill.getNextDueDate();
            if (due.equals(today.plusDays(bill.getRemindDaysBefore()))) {
                events.publishEvent(new BillDueSoon(bill.getId(), bill.getOwnerId(), bill.getName(),
                        bill.getAmount().roundedAmount(), bill.getAmount().currencyCode(), due,
                        bill.getRemindDaysBefore()));
                sent++;
            } else if (due.equals(today.minusDays(1))) {
                events.publishEvent(new BillOverdue(bill.getId(), bill.getOwnerId(), bill.getName(),
                        bill.getAmount().roundedAmount(), bill.getAmount().currencyCode(), due));
                sent++;
            }
        }
        return sent;
    }

    public void deleteAllOf(UUID ownerId) {
        bills.deleteByOwner(ownerId);
    }

    private WalletBalance wallet(UUID ownerId, UUID walletId) {
        if (walletId == null) {
            throw new DomainException("WALLET_REQUIRED", "Wallet is required");
        }
        WalletBalance wallet = ledger.walletBalances(ownerId).stream().filter(w -> w.walletId().equals(walletId))
                .findFirst().orElseThrow(() -> new NotFoundException("Wallet", walletId));
        if (wallet.archived()) {
            throw new DomainException("WALLET_ARCHIVED", "Wallet " + wallet.name() + " is archived");
        }
        return wallet;
    }

    private void requireExpenseCategory(UUID ownerId, UUID categoryId) {
        boolean expense = categoryId != null && ledger.categories(ownerId).stream()
                .anyMatch(c -> c.id().equals(categoryId) && "EXPENSE".equals(c.type()));
        if (!expense) {
            throw new DomainException("INVALID_CATEGORY", "A bill needs one of your expense categories");
        }
    }

    private static Money money(BigDecimal amount, WalletBalance wallet) {
        return amount == null ? null : new Money(amount, wallet.balance().currency());
    }

    private Bill load(UUID id, UUID ownerId) {
        return bills.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Bill", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
