package com.financemanage.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private static final UUID OWNER = UUID.randomUUID();

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final List<Notification> sent = new ArrayList<>();
    private NotificationService service;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Clock clock = Clock.fixed(Instant.parse("2025-06-24T00:00:00Z"), ZoneOffset.UTC);
        service = new NotificationService(repository, List.of(sent::add), clock);
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

        assertThat(n.getReadAt()).isEqualTo(Instant.parse("2025-06-24T00:00:00Z"));
        assertThatThrownBy(() -> service.markRead(n.getId(), UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
