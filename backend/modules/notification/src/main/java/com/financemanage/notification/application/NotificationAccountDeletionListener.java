package com.financemanage.notification.application;

import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import com.financemanage.notification.domain.NotificationRepository;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Deletes the notifications of a deleted account (after the deletion commits; retried through the outbox). */
@Component
class NotificationAccountDeletionListener {

    private final NotificationRepository notifications;

    NotificationAccountDeletionListener(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        notifications.deleteByOwnerId(event.userId());
    }
}
