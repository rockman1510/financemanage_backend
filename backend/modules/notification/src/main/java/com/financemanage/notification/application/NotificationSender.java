package com.financemanage.notification.application;

import com.financemanage.notification.domain.Notification;

/** Outbound port for delivering a notification (push, e-mail, SMS...). Add adapters without changing callers. */
public interface NotificationSender {

    void send(Notification notification);
}
