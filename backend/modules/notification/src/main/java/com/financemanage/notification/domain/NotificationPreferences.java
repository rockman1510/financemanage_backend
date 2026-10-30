package com.financemanage.notification.domain;

import com.financemanage.shared.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A user's notification settings, plus what the module needs to know for the daily reminder (time zone from the
 * profile, last day something was recorded). Disabled types are not notified at all; during quiet hours notifications
 * are kept in the app but not pushed.
 */
@Entity
@Table(name = "preferences", schema = "notification")
public class NotificationPreferences {

    public static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(20, 0);

    @Id
    @Column(name = "owner_id")
    private UUID ownerId;
    /** Comma-separated {@link NotificationType} names. */
    @Column(name = "disabled_types", nullable = false)
    private String disabledTypes = "";
    @Column(name = "daily_reminder_enabled", nullable = false)
    private boolean dailyReminderEnabled = true;
    @Column(name = "daily_reminder_time", nullable = false)
    private LocalTime dailyReminderTime = DEFAULT_REMINDER_TIME;
    @Column(name = "quiet_start")
    private LocalTime quietStart;
    @Column(name = "quiet_end")
    private LocalTime quietEnd;
    @Column(name = "time_zone", nullable = false)
    private String timeZone = "Asia/Ho_Chi_Minh";
    @Column(name = "last_recorded_on")
    private LocalDate lastRecordedOn;
    @Column(name = "last_reminder_on")
    private LocalDate lastReminderOn;

    protected NotificationPreferences() {
    }

    public NotificationPreferences(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public boolean isEnabled(NotificationType type) {
        return type == NotificationType.DAILY_REMINDER ? dailyReminderEnabled : !getDisabledTypes().contains(type);
    }

    /**
     * Replaces the settings. Quiet hours need both ends (may cross midnight, e.g. 22:00–07:00) or neither; the daily
     * reminder is sent at the start of its hour.
     */
    public void update(Set<NotificationType> disabled, boolean reminderEnabled, LocalTime reminderTime,
                       LocalTime newQuietStart, LocalTime newQuietEnd) {
        if ((newQuietStart == null) != (newQuietEnd == null)) {
            throw new DomainException("INVALID_QUIET_HOURS", "Quiet hours need a start and an end");
        }
        EnumSet<NotificationType> types = disabled == null || disabled.isEmpty()
                ? EnumSet.noneOf(NotificationType.class) : EnumSet.copyOf(disabled);
        types.remove(NotificationType.DAILY_REMINDER); // it has its own switch
        disabledTypes = types.stream().map(Enum::name).collect(Collectors.joining(","));
        dailyReminderEnabled = reminderEnabled;
        dailyReminderTime = (reminderTime == null ? DEFAULT_REMINDER_TIME : reminderTime).withMinute(0).withSecond(0)
                .withNano(0);
        quietStart = newQuietStart;
        quietEnd = newQuietEnd;
    }

    public boolean inQuietHours(Instant now) {
        if (quietStart == null) {
            return false;
        }
        LocalTime time = now.atZone(zone()).toLocalTime();
        return quietStart.isBefore(quietEnd)
                ? !time.isBefore(quietStart) && time.isBefore(quietEnd)
                : !time.isBefore(quietStart) || time.isBefore(quietEnd);
    }

    /** It is the reminder hour in the user's time zone and nothing was recorded or reminded today. */
    public boolean isDailyReminderDue(Instant now) {
        LocalDate today = today(now);
        return dailyReminderEnabled && now.atZone(zone()).getHour() == dailyReminderTime.getHour()
                && !today.equals(lastRecordedOn) && !today.equals(lastReminderOn);
    }

    public void recordedSomething(Instant now) {
        lastRecordedOn = today(now);
    }

    public void reminded(Instant now) {
        lastReminderOn = today(now);
    }

    public void changeTimeZone(String zone) {
        timeZone = ZoneId.of(zone).getId();
    }

    public LocalDate today(Instant now) {
        return now.atZone(zone()).toLocalDate();
    }

    private ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    public UUID getOwnerId() { return ownerId; }
    public boolean isDailyReminderEnabled() { return dailyReminderEnabled; }
    public LocalTime getDailyReminderTime() { return dailyReminderTime; }
    public LocalTime getQuietStart() { return quietStart; }
    public LocalTime getQuietEnd() { return quietEnd; }
    public String getTimeZone() { return timeZone; }

    public Set<NotificationType> getDisabledTypes() {
        return disabledTypes.isBlank() ? Set.of() : Arrays.stream(disabledTypes.split(","))
                .map(NotificationType::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
