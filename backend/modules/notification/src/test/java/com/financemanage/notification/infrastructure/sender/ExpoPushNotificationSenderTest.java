package com.financemanage.notification.infrastructure.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.NotificationType;
import com.financemanage.notification.domain.PushDevice;
import com.financemanage.notification.domain.PushDeviceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExpoPushNotificationSenderTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final PushDevice PHONE = new PushDevice("ExponentPushToken[aaaaaaaaaaaa]", OWNER, "ios", null,
            Instant.now());
    private static final PushDevice OLD_PHONE = new PushDevice("ExponentPushToken[bbbbbbbbbbbb]", OWNER, "android",
            null, Instant.now());

    private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
    private MockRestServiceServer server;
    private ExpoPushNotificationSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://exp.host");
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ExpoPushNotificationSender(builder.build(), devices);
        when(devices.findByOwnerId(OWNER)).thenReturn(List.of(PHONE, OLD_PHONE));
    }

    @Test
    void sendsOneMessagePerDeviceAndForgetsUninstalledApps() {
        server.expect(requestTo("https://exp.host/--/api/v2/push/send")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("\"to\":\"ExponentPushToken[aaaaaaaaaaaa]\""),
                        containsString("\"title\":\"Hoá đơn sắp đến hạn\""),
                        containsString("\"type\":\"BILL_DUE_SOON\""))))
                .andRespond(withSuccess("""
                        {"data":[{"status":"ok","id":"t1"},
                                 {"status":"error","message":"not registered",
                                  "details":{"error":"DeviceNotRegistered"}}]}""", MediaType.APPLICATION_JSON));

        sender.send(notification());

        server.verify();
        verify(devices).delete(OLD_PHONE);
        verify(devices, never()).delete(PHONE);
    }

    @Test
    void anExpoOutageIsLoggedNotThrown() {
        server.expect(requestTo("https://exp.host/--/api/v2/push/send")).andRespond(withServerError());

        sender.send(notification());

        server.verify();
        verify(devices, never()).delete(ArgumentMatchers.any());
        assertThat(PHONE.getToken()).isNotBlank();
    }

    private static Notification notification() {
        return new Notification(OWNER, NotificationType.BILL_DUE_SOON, "Hoá đơn sắp đến hạn", "Tiền điện", "BILL",
                UUID.randomUUID(), Instant.now());
    }
}
