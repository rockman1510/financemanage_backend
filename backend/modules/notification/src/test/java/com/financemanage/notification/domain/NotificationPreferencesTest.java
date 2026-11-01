package com.financemanage.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.shared.domain.DomainException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPreferencesTest {

    /** Times are given in Ho Chi Minh City (UTC+7). */
    private static Instant local(String time) {
        return Instant.parse("2025-06-24T" + time + ":00Z").minusSeconds(7 * 3600);
    }

    @Test
    void quietHoursMayCrossMidnight() {
        NotificationPreferences prefs = new NotificationPreferences(UUID.randomUUID());
        prefs.update(Set.of(), true, null, LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertThat(prefs.inQuietHours(local("23:30"))).isTrue();
        assertThat(prefs.inQuietHours(local("06:59"))).isTrue();
        assertThat(prefs.inQuietHours(local("07:00"))).isFalse();
        assertThat(prefs.inQuietHours(local("12:00"))).isFalse();
        assertThatThrownBy(() -> prefs.update(Set.of(), true, null, LocalTime.NOON, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_QUIET_HOURS");
    }

    @Test
    void dailyReminderIsDueAtItsHourWhenNothingWasRecordedToday() {
        NotificationPreferences prefs = new NotificationPreferences(UUID.randomUUID());
        prefs.update(Set.of(NotificationType.DAILY_REMINDER), true, LocalTime.of(21, 30), null, null);

        assertThat(prefs.getDailyReminderTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(prefs.isEnabled(NotificationType.DAILY_REMINDER)).isTrue(); // has its own switch
        assertThat(prefs.isDailyReminderDue(local("20:00"))).isFalse();
        assertThat(prefs.isDailyReminderDue(local("21:00"))).isTrue();
        prefs.recordedSomething(local("09:00"));
        assertThat(prefs.isDailyReminderDue(local("21:00"))).isFalse();
    }

    @Test
    void reminderHourFollowsTheTimeZone() {
        NotificationPreferences prefs = new NotificationPreferences(UUID.randomUUID());
        prefs.changeTimeZone("Europe/Paris");

        assertThat(prefs.isDailyReminderDue(Instant.parse("2025-06-24T18:00:00Z"))).isTrue(); // 20:00 in Paris
    }
}
