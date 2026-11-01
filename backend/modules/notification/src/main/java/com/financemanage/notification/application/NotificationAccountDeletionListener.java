package com.financemanage.notification.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.notification.domain.NotificationPreferencesRepository;
import com.financemanage.notification.domain.NotificationRepository;
import com.financemanage.notification.domain.PushDeviceRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Deletes the notifications, push devices and settings of a deleted account (after the deletion commits; retried
 * through the outbox).
 */
@Component
class NotificationAccountDeletionListener {

    private final NotificationRepository notifications;
    private final PushDeviceRepository devices;
    private final NotificationPreferencesRepository preferences;

    NotificationAccountDeletionListener(NotificationRepository notifications, PushDeviceRepository devices,
                                        NotificationPreferencesRepository preferences) {
        this.notifications = notifications;
        this.devices = devices;
        this.preferences = preferences;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        notifications.deleteByOwnerId(event.userId());
        devices.deleteByOwnerId(event.userId());
        preferences.deleteById(event.userId());
    }
}
