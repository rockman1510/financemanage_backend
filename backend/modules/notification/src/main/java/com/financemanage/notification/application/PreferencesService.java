package com.financemanage.notification.application;

import com.financemanage.notification.domain.NotificationPreferences;
import com.financemanage.notification.domain.NotificationPreferencesRepository;
import com.financemanage.notification.domain.NotificationType;
import java.time.Clock;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notification settings, and the facts the daily reminder needs (time zone, last day something was recorded). */
@Service
@Transactional
public class PreferencesService {

    private final NotificationPreferencesRepository preferences;
    private final Clock clock;

    public PreferencesService(NotificationPreferencesRepository preferences, Clock clock) {
        this.preferences = preferences;
        this.clock = clock;
    }

    /** The user's settings (defaults when never changed). */
    @Transactional(readOnly = true)
    public NotificationPreferences get(UUID ownerId) {
        return preferences.findById(ownerId).orElseGet(() -> new NotificationPreferences(ownerId));
    }

    public NotificationPreferences update(UUID ownerId, Set<NotificationType> disabledTypes, boolean dailyReminder,
                                          LocalTime reminderTime, LocalTime quietStart, LocalTime quietEnd) {
        NotificationPreferences prefs = getOrCreate(ownerId);
        prefs.update(disabledTypes, dailyReminder, reminderTime, quietStart, quietEnd);
        return preferences.save(prefs);
    }

    /** Something was recorded today: no daily reminder needed. */
    public void recordedSomething(UUID ownerId) {
        NotificationPreferences prefs = getOrCreate(ownerId);
        prefs.recordedSomething(clock.instant());
        preferences.save(prefs);
    }

    public void timeZoneChanged(UUID ownerId, String timeZone) {
        NotificationPreferences prefs = getOrCreate(ownerId);
        prefs.changeTimeZone(timeZone);
        preferences.save(prefs);
    }

    private NotificationPreferences getOrCreate(UUID ownerId) {
        return preferences.findById(ownerId).orElseGet(() -> new NotificationPreferences(ownerId));
    }
}
