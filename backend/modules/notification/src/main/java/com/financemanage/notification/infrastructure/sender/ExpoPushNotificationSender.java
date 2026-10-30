package com.financemanage.notification.infrastructure.sender;

import com.financemanage.notification.application.NotificationSender;
import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.PushDevice;
import com.financemanage.notification.domain.PushDeviceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends push notifications through the Expo Push API (which forwards to FCM and APNs), the simplest option for an
 * Expo app. Tokens Expo reports as {@code DeviceNotRegistered} (app uninstalled) are removed. A failure is logged,
 * never thrown: the notification is still in the app.
 */
class ExpoPushNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushNotificationSender.class);
    static final String SEND_PATH = "/--/api/v2/push/send";
    private static final int MAX_BATCH = 100;

    private final RestClient client;
    private final PushDeviceRepository devices;

    ExpoPushNotificationSender(RestClient client, PushDeviceRepository devices) {
        this.client = client;
        this.devices = devices;
    }

    record Message(String to, String title, String body, String sound, Map<String, Object> data) {
    }

    record Ticket(String status, String message, Map<String, Object> details) {
    }

    record Response(List<Ticket> data) {
    }

    @Override
    public void send(Notification notification) {
        List<PushDevice> targets = devices.findByOwnerId(notification.getOwnerId());
        for (int from = 0; from < targets.size(); from += MAX_BATCH) {
            sendBatch(notification, targets.subList(from, Math.min(from + MAX_BATCH, targets.size())));
        }
    }

    private void sendBatch(Notification notification, List<PushDevice> batch) {
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", notification.getId().toString());
        data.put("type", notification.getType().name());
        if (notification.getReferenceType() != null) {
            data.put("referenceType", notification.getReferenceType());
            data.put("referenceId", String.valueOf(notification.getReferenceId()));
        }
        List<Message> messages = new ArrayList<>();
        batch.forEach(d -> messages.add(new Message(d.getToken(), notification.getTitle(), notification.getBody(),
                "default", data)));
        try {
            Response response = client.post().uri(SEND_PATH).contentType(MediaType.APPLICATION_JSON)
                    .body(messages).retrieve().body(Response.class);
            List<Ticket> tickets = response == null || response.data() == null ? List.of() : response.data();
            for (int i = 0; i < tickets.size() && i < batch.size(); i++) {
                Ticket ticket = tickets.get(i);
                if ("error".equals(ticket.status())) {
                    Object error = ticket.details() == null ? null : ticket.details().get("error");
                    if ("DeviceNotRegistered".equals(error)) {
                        devices.delete(batch.get(i));
                    } else {
                        log.warn("Expo push to {} failed: {} {}", batch.get(i).getToken(), error, ticket.message());
                    }
                }
            }
        } catch (RestClientException e) {
            log.warn("Expo push for notification {} failed: {}", notification.getId(), e.getMessage());
        }
    }
}
