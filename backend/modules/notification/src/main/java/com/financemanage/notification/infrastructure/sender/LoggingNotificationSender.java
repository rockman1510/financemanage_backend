package com.financemanage.notification.infrastructure.sender;

import com.financemanage.notification.application.NotificationSender;
import com.financemanage.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default adapter. Replace/extend with an FCM/APNs adapter for real push notifications. */
@Component
class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(Notification notification) {
        log.info("Notify {} [{}] {}", notification.getOwnerId(), notification.getType(), notification.getTitle());
    }
}
