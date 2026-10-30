package com.financemanage.notification.application;

import com.financemanage.bill.api.BillEvents.BillDueSoon;
import com.financemanage.bill.api.BillEvents.BillOverdue;
import com.financemanage.bill.api.BillEvents.BillPaymentFailed;
import com.financemanage.budget.api.BudgetEvents.BudgetThresholdReached;
import com.financemanage.deposit.api.DepositEvents.DepositMatured;
import com.financemanage.deposit.api.DepositEvents.DepositMaturingSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentDueSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentOverdue;
import com.financemanage.loan.api.LoanEvents.LoanPaidOff;
import com.financemanage.saving.api.SavingEvents.SavingGoalMilestone;
import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.NotificationPreferences;
import com.financemanage.notification.domain.NotificationPreferencesRepository;
import com.financemanage.notification.domain.NotificationRepository;
import com.financemanage.notification.domain.NotificationType;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns domain events of the other modules into notifications. {@link ApplicationModuleListener} runs after the
 * publishing transaction commits, asynchronously, and is tracked by the event publication registry (outbox) so
 * failed deliveries can be retried. Each notification has a dedup key, so a fact is notified once even when a job or
 * a delivery runs twice; the user's preferences decide whether it is created and whether it is pushed.
 */
@Service
@Transactional
public class NotificationService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationRepository repository;
    private final NotificationPreferencesRepository preferences;
    private final List<NotificationSender> senders;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, NotificationPreferencesRepository preferences,
                               List<NotificationSender> senders, Clock clock) {
        this.repository = repository;
        this.preferences = preferences;
        this.senders = senders;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void on(DepositMaturingSoon event) {
        notify(event.ownerId(), NotificationType.DEPOSIT_MATURING_SOON, "Sổ tiết kiệm sắp đáo hạn",
                "Sổ tại %s đáo hạn ngày %s (còn %d ngày).".formatted(event.institutionName(),
                        DATE.format(event.maturityDate()), event.daysLeft()),
                "DEPOSIT", event.depositId(), "maturing:" + event.depositId() + ":" + event.maturityDate());
    }

    @ApplicationModuleListener
    public void on(DepositMatured event) {
        String body = event.rolledOver()
                ? "Sổ tại %s đã đáo hạn và được tái tục với gốc %s.".formatted(event.institutionName(),
                        money(event.newPrincipal(), event.currency()))
                : "Sổ tại %s đã đáo hạn ngày %s. Hãy tất toán hoặc gửi lại.".formatted(event.institutionName(),
                        DATE.format(event.maturityDate()));
        notify(event.ownerId(), NotificationType.DEPOSIT_MATURED, "Sổ tiết kiệm đã đáo hạn", body, "DEPOSIT",
                event.depositId(), "matured:" + event.depositId() + ":" + event.maturityDate());
    }

    @ApplicationModuleListener
    public void on(InstallmentDueSoon event) {
        String action = event.borrowed() ? "Kỳ trả nợ cho" : "Kỳ thu nợ từ";
        notify(event.ownerId(), NotificationType.INSTALLMENT_DUE_SOON, "Sắp đến hạn thanh toán",
                "%s %s: %s vào ngày %s.".formatted(action, event.counterpartyName(),
                        money(event.amount(), event.currency()), DATE.format(event.dueDate())),
                "LOAN", event.loanId(), "due:" + event.loanId() + ":" + event.dueDate());
    }

    @ApplicationModuleListener
    public void on(InstallmentOverdue event) {
        String action = event.borrowed() ? "Khoản trả nợ cho" : "Khoản thu nợ từ";
        notify(event.ownerId(), NotificationType.INSTALLMENT_OVERDUE, "Quá hạn thanh toán",
                "%s %s (%s, hạn %s) đã quá hạn.".formatted(action, event.counterpartyName(),
                        money(event.amount(), event.currency()), DATE.format(event.dueDate())),
                "LOAN", event.loanId(), "overdue:" + event.loanId() + ":" + event.dueDate());
    }

    @ApplicationModuleListener
    public void on(LoanPaidOff event) {
        notify(event.ownerId(), NotificationType.LOAN_PAID_OFF, "Đã tất toán khoản vay",
                "Khoản vay với %s đã được thanh toán đầy đủ.".formatted(event.counterpartyName()), "LOAN",
                event.loanId(), "paid-off:" + event.loanId());
    }

    @ApplicationModuleListener
    public void on(BudgetThresholdReached event) {
        boolean exceeded = event.percent() >= 100;
        notify(event.ownerId(), exceeded ? NotificationType.BUDGET_EXCEEDED : NotificationType.BUDGET_WARNING,
                exceeded ? "Vượt ngân sách" : "Sắp hết ngân sách",
                "Ngân sách \"%s\" đã dùng %s / %s (%d%%).".formatted(event.name(),
                        money(event.spent(), event.currency()), money(event.amount(), event.currency()),
                        event.percent()),
                "BUDGET", event.budgetId(),
                "budget:" + event.budgetId() + ":" + event.periodStart() + ":" + event.percent());
    }

    @ApplicationModuleListener
    public void on(BillDueSoon event) {
        notify(event.ownerId(), NotificationType.BILL_DUE_SOON, "Hoá đơn sắp đến hạn",
                "%s: %s, hạn %s (còn %d ngày).".formatted(event.name(), money(event.amount(), event.currency()),
                        DATE.format(event.dueDate()), event.daysLeft()),
                "BILL", event.billId(), "bill-due:" + event.billId() + ":" + event.dueDate());
    }

    @ApplicationModuleListener
    public void on(BillOverdue event) {
        notify(event.ownerId(), NotificationType.BILL_OVERDUE, "Hoá đơn quá hạn",
                "%s (%s) đã quá hạn ngày %s.".formatted(event.name(), money(event.amount(), event.currency()),
                        DATE.format(event.dueDate())),
                "BILL", event.billId(), "bill-overdue:" + event.billId() + ":" + event.dueDate());
    }

    @ApplicationModuleListener
    public void on(BillPaymentFailed event) {
        notify(event.ownerId(), NotificationType.BILL_PAYMENT_FAILED, "Thanh toán hoá đơn không thành công",
                "Không ghi được khoản thanh toán \"%s\" (%s). Hoá đơn vẫn đến hạn.".formatted(event.name(),
                        event.reason()),
                "BILL", event.billId(), "bill-failed:" + event.paymentId());
    }

    @ApplicationModuleListener
    public void on(SavingGoalMilestone event) {
        boolean reached = event.percent() >= 100;
        notify(event.ownerId(), NotificationType.SAVING_GOAL_MILESTONE,
                reached ? "Đã đạt mục tiêu tiết kiệm" : "Đã được nửa chặng đường",
                "Mục tiêu \"%s\": %s / %s (%d%%).".formatted(event.name(), money(event.saved(), event.currency()),
                        money(event.target(), event.currency()), event.percent()),
                "SAVING_GOAL", event.goalId(), "saving:" + event.goalId() + ":" + event.percent());
    }

    /** Sends the evening reminder to users who recorded nothing today (job, every hour). */
    public int sendDailyReminders() {
        Instant now = clock.instant();
        int sent = 0;
        for (NotificationPreferences prefs : preferences.findByDailyReminderEnabledTrue()) {
            if (prefs.isDailyReminderDue(now)) {
                prefs.reminded(now);
                notify(prefs.getOwnerId(), NotificationType.DAILY_REMINDER, "Ghi chép hôm nay",
                        "Hôm nay bạn chưa ghi khoản thu chi nào. Ghi lại ngay để không quên nhé!", null, null,
                        "daily:" + prefs.today(now));
                sent++;
            }
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public List<Notification> latest(UUID ownerId) {
        return repository.findTop50ByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID ownerId) {
        return repository.countByOwnerIdAndReadAtIsNull(ownerId);
    }

    public void markRead(UUID id, UUID ownerId) {
        repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Notification", id))
                .markRead(clock.instant());
    }

    public int markAllRead(UUID ownerId) {
        return repository.markAllRead(ownerId, clock.instant());
    }

    private void notify(UUID ownerId, NotificationType type, String title, String body, String referenceType,
                        UUID referenceId, String dedupKey) {
        NotificationPreferences prefs = preferences.findById(ownerId).orElseGet(() -> new NotificationPreferences(
                ownerId));
        if (!prefs.isEnabled(type) || repository.existsByOwnerIdAndDedupKey(ownerId, dedupKey)) {
            return;
        }
        Instant now = clock.instant();
        Notification notification = repository.save(
                new Notification(ownerId, type, title, body, referenceType, referenceId, dedupKey, now));
        if (!prefs.inQuietHours(now)) {
            senders.forEach(sender -> sender.send(notification));
        }
    }

    private static String money(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " " + currency;
    }
}
