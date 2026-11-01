package com.financemanage.notification.infrastructure.sender;

import com.financemanage.notification.application.NotificationSender;
import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.PushDevice;
import com.financemanage.notification.domain.PushDeviceRepository;
import com.financemanage.notification.domain.PushProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends push notifications through the Firebase Cloud Messaging HTTP v1 API to devices registered with an FCM token
 * (Android directly, iOS through the APNs key uploaded to the Firebase project). One request per device, as the v1 API
 * has no batch call. Tokens FCM reports as gone (app uninstalled, token from another project) are removed. A failure
 * is logged, never thrown: the notification is still in the app.
 */
class FcmPushNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushNotificationSender.class);
    /** FcmError codes meaning the token will never work again. */
    private static final Set<String> DEAD_TOKEN = Set.of("UNREGISTERED", "SENDER_ID_MISMATCH");
    static final String ANDROID_CHANNEL = "default";

    private final RestClient client;
    private final GoogleAccessTokens tokens;
    private final String sendPath;
    private final PushDeviceRepository devices;

    FcmPushNotificationSender(RestClient client, GoogleAccessTokens tokens, String projectId,
                              PushDeviceRepository devices) {
        this.client = client;
        this.tokens = tokens;
        this.sendPath = "/v1/projects/" + projectId + "/messages:send";
        this.devices = devices;
    }

    @Override
    public void send(Notification notification) {
        for (PushDevice device : devices.findByOwnerIdAndProvider(notification.getOwnerId(), PushProvider.FCM)) {
            try {
                sendTo(device, notification, true);
            } catch (RestClientException | IllegalStateException e) {
                log.warn("FCM push of notification {} failed: {}", notification.getId(), e.getMessage());
            }
        }
    }

    private void sendTo(PushDevice device, Notification notification, boolean retryUnauthorized) {
        try {
            client.post().uri(sendPath).contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token())
                    .body(Map.of("message", message(device.getToken(), notification)))
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED && retryUnauthorized) {
                tokens.invalidate(); // revoked or expired early: get a new access token once
                sendTo(device, notification, false);
            } else if (isDeadToken(e)) {
                devices.delete(device);
            } else {
                log.warn("FCM push to {} failed: {} {}", shortToken(device), e.getStatusCode(),
                        e.getResponseBodyAsString());
            }
        }
    }

    /** Title and body shown by the system; {@code data} lets the app open the right screen when tapped. */
    static Map<String, Object> message(String token, Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", notification.getId().toString());
        data.put("type", notification.getType().name());
        if (notification.getReferenceType() != null) {
            data.put("referenceType", notification.getReferenceType());
            data.put("referenceId", String.valueOf(notification.getReferenceId()));
        }
        return Map.of(
                "token", token,
                "notification", Map.of("title", notification.getTitle(), "body", notification.getBody()),
                "data", data,
                "android", Map.of("priority", "HIGH",
                        "notification", Map.of("channel_id", ANDROID_CHANNEL, "sound", "default")),
                "apns", Map.of("payload", Map.of("aps", Map.of("sound", "default"))));
    }

    /** 404 UNREGISTERED, or an INVALID_ARGUMENT about the registration token itself. */
    private static boolean isDeadToken(HttpStatusCodeException e) {
        String body = e.getResponseBodyAsString();
        for (String code : DEAD_TOKEN) {
            if (body.contains("\"" + code + "\"")) {
                return true;
            }
        }
        return e.getStatusCode() == HttpStatus.BAD_REQUEST && body.contains("registration token");
    }

    private static String shortToken(PushDevice device) {
        String token = device.getToken();
        return token.length() <= 12 ? token : token.substring(0, 12) + "…";
    }
}
