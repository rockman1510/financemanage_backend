package com.financemanage.notification.application;

import com.financemanage.deposit.api.DepositEvents.DepositMatured;
import com.financemanage.deposit.api.DepositEvents.DepositMaturingSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentDueSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentOverdue;
import com.financemanage.loan.api.LoanEvents.LoanPaidOff;
import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.NotificationRepository;
import com.financemanage.notification.domain.NotificationType;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to domain events of the deposit and loan modules. {@link ApplicationModuleListener} runs after the
 * publishing transaction commits, asynchronously, and is tracked by the event publication registry (outbox) so
 * failed deliveries can be retried.
 */
@Service
@Transactional
public class NotificationService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationRepository repository;
    private final List<NotificationSender> senders;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, List<NotificationSender> senders, Clock clock) {
        this.repository = repository;
        this.senders = senders;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void on(DepositMaturingSoon event) {
        notify(event.ownerId(), NotificationType.DEPOSIT_MATURING_SOON, "Sổ tiết kiệm sắp đáo hạn",
                "Sổ tại %s đáo hạn ngày %s (còn %d ngày).".formatted(event.institutionName(),
                        DATE.format(event.maturityDate()), event.daysLeft()),
                "DEPOSIT", event.depositId());
    }

    @ApplicationModuleListener
    public void on(DepositMatured event) {
        String body = event.rolledOver()
                ? "Sổ tại %s đã đáo hạn và được tái tục với gốc %s.".formatted(event.institutionName(),
                        money(event.newPrincipal(), event.currency()))
                : "Sổ tại %s đã đáo hạn ngày %s. Hãy tất toán hoặc gửi lại.".formatted(event.institutionName(),
                        DATE.format(event.maturityDate()));
        notify(event.ownerId(), NotificationType.DEPOSIT_MATURED, "Sổ tiết kiệm đã đáo hạn", body, "DEPOSIT",
                event.depositId());
    }

    @ApplicationModuleListener
    public void on(InstallmentDueSoon event) {
        String action = event.borrowed() ? "Kỳ trả nợ cho" : "Kỳ thu nợ từ";
        notify(event.ownerId(), NotificationType.INSTALLMENT_DUE_SOON, "Sắp đến hạn thanh toán",
                "%s %s: %s vào ngày %s.".formatted(action, event.counterpartyName(),
                        money(event.amount(), event.currency()), DATE.format(event.dueDate())),
                "LOAN", event.loanId());
    }

    @ApplicationModuleListener
    public void on(InstallmentOverdue event) {
        String action = event.borrowed() ? "Khoản trả nợ cho" : "Khoản thu nợ từ";
        notify(event.ownerId(), NotificationType.INSTALLMENT_OVERDUE, "Quá hạn thanh toán",
                "%s %s (%s, hạn %s) đã quá hạn.".formatted(action, event.counterpartyName(),
                        money(event.amount(), event.currency()), DATE.format(event.dueDate())),
                "LOAN", event.loanId());
    }

    @ApplicationModuleListener
    public void on(LoanPaidOff event) {
        notify(event.ownerId(), NotificationType.LOAN_PAID_OFF, "Đã tất toán khoản vay",
                "Khoản vay với %s đã được thanh toán đầy đủ.".formatted(event.counterpartyName()), "LOAN",
                event.loanId());
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

    private void notify(UUID ownerId, NotificationType type, String title, String body, String referenceType,
                        UUID referenceId) {
        Notification notification = repository.save(
                new Notification(ownerId, type, title, body, referenceType, referenceId, clock.instant()));
        senders.forEach(sender -> sender.send(notification));
    }

    private static String money(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " " + currency;
    }
}
