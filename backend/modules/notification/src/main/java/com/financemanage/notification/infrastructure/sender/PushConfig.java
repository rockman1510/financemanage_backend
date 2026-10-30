package com.financemanage.notification.infrastructure.sender;

import com.financemanage.notification.domain.PushDeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Expo push is on by default ({@code app.notification.expo.enabled}); an access token is only needed when "enhanced
 * push security" is turned on in the Expo project.
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
}
