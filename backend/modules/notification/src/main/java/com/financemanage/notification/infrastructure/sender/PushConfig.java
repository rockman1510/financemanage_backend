package com.financemanage.notification.infrastructure.sender;

import com.financemanage.notification.domain.PushDeviceRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Push adapters; each sends only to devices registered with its provider.
 * <ul>
 *   <li>Expo ({@code app.notification.expo.enabled}, on by default): the Expo app. An access token is only needed when
 *       "enhanced push security" is turned on in the Expo project.</li>
 *   <li>FCM ({@code app.notification.fcm.enabled}, off by default): the KMP app. Needs the Firebase project's
 *       service-account key file ({@code credentials-file}, or its content in {@code credentials-json}); a missing or
 *       broken key stops the application at start-up rather than silently sending nothing.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class PushConfig {

    @Bean
    @ConditionalOnProperty(name = "app.notification.expo.enabled", havingValue = "true", matchIfMissing = true)
    ExpoPushNotificationSender expoPushNotificationSender(
            PushDeviceRepository devices,
            @Value("${app.notification.expo.base-url:https://exp.host}") String baseUrl,
            @Value("${app.notification.expo.access-token:}") String accessToken) {
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (!accessToken.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return new ExpoPushNotificationSender(builder.build(), devices);
    }

    @Bean
    @ConditionalOnProperty(name = "app.notification.fcm.enabled", havingValue = "true")
    FcmPushNotificationSender fcmPushNotificationSender(
            PushDeviceRepository devices, Clock clock,
            @Value("${app.notification.fcm.credentials-file:}") String credentialsFile,
            @Value("${app.notification.fcm.credentials-json:}") String credentialsJson,
            @Value("${app.notification.fcm.base-url:https://fcm.googleapis.com}") String baseUrl) {
        ServiceAccount account = ServiceAccount.parse(credentials(credentialsFile, credentialsJson));
        GoogleAccessTokens tokens = new GoogleAccessTokens(account, RestClient.create(), clock);
        return new FcmPushNotificationSender(RestClient.builder().baseUrl(baseUrl).build(), tokens,
                account.projectId(), devices);
    }

    private static String credentials(String file, String json) {
        if (!json.isBlank()) {
            return json;
        }
        if (file.isBlank()) {
            throw new IllegalStateException("FCM is enabled but no service-account key is configured: set "
                    + "app.notification.fcm.credentials-file (FCM_CREDENTIALS_FILE) or credentials-json");
        }
        try {
            return Files.readString(Path.of(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the FCM service-account key file " + file, e);
        }
    }
}
