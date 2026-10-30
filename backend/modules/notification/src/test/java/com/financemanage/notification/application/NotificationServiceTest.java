package com.financemanage.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.financemanage.bill.api.BillEvents.BillDueSoon;
import com.financemanage.budget.api.BudgetEvents.BudgetThresholdReached;
import com.financemanage.deposit.api.DepositEvents.DepositMatured;
import com.financemanage.deposit.api.DepositEvents.DepositMaturingSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentDueSoon;
import com.financemanage.loan.api.LoanEvents.InstallmentOverdue;
import com.financemanage.loan.api.LoanEvents.LoanPaidOff;
import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.NotificationPreferences;
import com.financemanage.notification.domain.NotificationPreferencesRepository;
import com.financemanage.notification.domain.NotificationRepository;
import com.financemanage.notification.domain.NotificationType;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationPreferencesRepository preferences = mock(NotificationPreferencesRepository.class);
    private final List<Notification> sent = new ArrayList<>();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 2025-06-24 20:00 in Ho Chi Minh City
        Clock clock = Clock.fixed(Instant.parse("2025-06-24T13:00:00Z"), ZoneOffset.UTC);
        service = new NotificationService(repository, preferences, List.of(sent::add), clock);
    }

    @Test
    void depositMaturingSoonCreatesReminder() {
        service.on(new DepositMaturingSoon(UUID.randomUUID(), OWNER, "Vietcombank", LocalDate.of(2025, 7, 1), 7));

        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.DEPOSIT_MATURING_SOON);
            assertThat(n.getBody()).contains("Vietcombank", "01/07/2025", "7 ngày");
            assertThat(n.getOwnerId()).isEqualTo(OWNER);
        });
    }

    @Test
    void depositMaturedMessageDependsOnRollover() {
        service.on(new DepositMatured(UUID.randomUUID(), OWNER, "ACB", LocalDate.of(2025, 7, 1), true,
                new BigDecimal("102975342"), "VND"));
        service.on(new DepositMatured(UUID.randomUUID(), OWNER, "ACB", LocalDate.of(2025, 7, 1), false,
                new BigDecimal("100000000"), "VND"));

        assertThat(sent.get(0).getBody()).contains("tái tục", "102.975.342 VND");
        assertThat(sent.get(1).getBody()).contains("tất toán");
    }

    @Test
    void loanEventsUseDirectionSpecificWording() {
        service.on(new InstallmentDueSoon(UUID.randomUUID(), OWNER, "VPBank", true, LocalDate.of(2025, 7, 1),
                new BigDecimal("4080000"), "VND"));
        service.on(new InstallmentOverdue(UUID.randomUUID(), OWNER, "Bình", false, LocalDate.of(2025, 6, 23),
                new BigDecimal("500000"), "VND"));
        service.on(new LoanPaidOff(UUID.randomUUID(), OWNER, "VPBank"));

        assertThat(sent).extracting(Notification::getType).containsExactly(NotificationType.INSTALLMENT_DUE_SOON,
                NotificationType.INSTALLMENT_OVERDUE, NotificationType.LOAN_PAID_OFF);
        assertThat(sent.get(0).getBody()).startsWith("Kỳ trả nợ cho VPBank");
        assertThat(sent.get(1).getBody()).startsWith("Khoản thu nợ từ Bình");
    }

    @Test
    void markReadOnlyForOwner() {
        Notification n = new Notification(OWNER, NotificationType.LOAN_PAID_OFF, "t", "b", null, null,
                Instant.now());
        when(repository.findByIdAndOwnerId(n.getId(), OWNER)).thenReturn(Optional.of(n));

        service.markRead(n.getId(), OWNER);

        assertThat(n.getReadAt()).isEqualTo(Instant.parse("2025-06-24T13:00:00Z"));
        assertThatThrownBy(() -> service.markRead(n.getId(), UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void budgetAndBillEventsAreNotified() {
        UUID budget = UUID.randomUUID();
        service.on(new BudgetThresholdReached(budget, OWNER, "Ăn uống", 80, new BigDecimal("850000"),
                new BigDecimal("1000000"), "VND", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)));
        service.on(new BudgetThresholdReached(budget, OWNER, "Ăn uống", 100, new BigDecimal("1050000"),
                new BigDecimal("1000000"), "VND", LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)));
        service.on(new BillDueSoon(UUID.randomUUID(), OWNER, "Tiền điện", new BigDecimal("650000"), "VND",
                LocalDate.of(2025, 6, 27), 3));

        assertThat(sent).extracting(Notification::getType).containsExactly(NotificationType.BUDGET_WARNING,
                NotificationType.BUDGET_EXCEEDED, NotificationType.BILL_DUE_SOON);
        assertThat(sent.get(0).getBody()).contains("850.000 VND / 1.000.000 VND (80%)");
        assertThat(sent.get(2).getBody()).contains("Tiền điện", "27/06/2025", "còn 3 ngày");
    }

    @Test
    void aFactAlreadyNotifiedIsNotNotifiedAgain() {
        when(repository.existsByOwnerIdAndDedupKey(any(), any())).thenReturn(true);

        service.on(new LoanPaidOff(UUID.randomUUID(), OWNER, "VPBank"));

        assertThat(sent).isEmpty();
    }

    @Test
    void disabledTypesAreSkippedAndQuietHoursOnlySilenceThePush() {
        NotificationPreferences prefs = new NotificationPreferences(OWNER);
        prefs.update(Set.of(NotificationType.LOAN_PAID_OFF), true, null, LocalTime.of(19, 0), LocalTime.of(7, 0));
        when(preferences.findById(OWNER)).thenReturn(Optional.of(prefs));
        List<Notification> saved = new ArrayList<>();
        when(repository.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        service.on(new LoanPaidOff(UUID.randomUUID(), OWNER, "VPBank"));
        service.on(new InstallmentOverdue(UUID.randomUUID(), OWNER, "Bình", false, LocalDate.of(2025, 6, 23),
                new BigDecimal("500000"), "VND"));

        assertThat(saved).extracting(Notification::getType).containsExactly(NotificationType.INSTALLMENT_OVERDUE);
        assertThat(sent).isEmpty(); // 20:00 is inside 19:00–07:00
    }

    @Test
    void dailyReminderOnlyForUsersWhoRecordedNothingToday() {
        UUID busy = UUID.randomUUID();
        NotificationPreferences idle = new NotificationPreferences(OWNER);
        NotificationPreferences active = new NotificationPreferences(busy);
        active.recordedSomething(Instant.parse("2025-06-24T03:00:00Z"));
        when(preferences.findByDailyReminderEnabledTrue()).thenReturn(List.of(idle, active));

        assertThat(service.sendDailyReminders()).isEqualTo(1);
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.getOwnerId()).isEqualTo(OWNER);
            assertThat(n.getType()).isEqualTo(NotificationType.DAILY_REMINDER);
            assertThat(n.getDedupKey()).isEqualTo("daily:2025-06-24");
        });
        assertThat(service.sendDailyReminders()).isZero(); // already reminded today
    }
}
