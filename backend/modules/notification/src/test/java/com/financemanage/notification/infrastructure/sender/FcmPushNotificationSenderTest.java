package com.financemanage.notification.infrastructure.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.financemanage.notification.domain.Notification;
import com.financemanage.notification.domain.NotificationType;
import com.financemanage.notification.domain.PushDevice;
import com.financemanage.notification.domain.PushDeviceRepository;
import com.financemanage.notification.domain.PushProvider;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FcmPushNotificationSenderTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/finance-demo/messages:send";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final PushDevice PIXEL = new PushDevice("fcm-token-pixel:" + "a".repeat(140), OWNER,
            PushProvider.FCM, "android", "Pixel 8", NOW);
    private static final PushDevice IPHONE = new PushDevice("fcm-token-iphone:" + "b".repeat(140), OWNER,
            PushProvider.FCM, "ios", "iPhone", NOW);

    private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
    private KeyPair keys;
    private ServiceAccount account;
    private MockRestServiceServer google;
    private MockRestServiceServer fcm;
    private FcmPushNotificationSender sender;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        account = ServiceAccount.parse(serviceAccountJson());
        RestClient.Builder tokenClient = RestClient.builder();
        google = MockRestServiceServer.bindTo(tokenClient).build();
        RestClient.Builder fcmClient = RestClient.builder().baseUrl("https://fcm.googleapis.com");
        fcm = MockRestServiceServer.bindTo(fcmClient).build();
        GoogleAccessTokens tokens = new GoogleAccessTokens(account, tokenClient.build(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        sender = new FcmPushNotificationSender(fcmClient.build(), tokens, account.projectId(), devices);
        when(devices.findByOwnerIdAndProvider(OWNER, PushProvider.FCM)).thenReturn(List.of(PIXEL, IPHONE));
    }

    @Test
    void signsInWithTheServiceAccountOnceAndSendsOneMessagePerDevice() throws Exception {
        AtomicReference<String> assertion = new AtomicReference<>();
        google.expect(once(), requestTo(TOKEN_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type"
                        + "%3Ajwt-bearer")))
                .andExpect(request -> assertion.set(formValue(((MockClientHttpRequest) request).getBodyAsString(),
                        "assertion")))
                .andRespond(withSuccess("{\"access_token\":\"ya29.token\",\"expires_in\":3599}",
                        MediaType.APPLICATION_JSON));
        fcm.expect(requestTo(SEND_URL)).andExpect(header("Authorization", "Bearer ya29.token"))
                .andExpect(content().string(allOf(containsString("\"token\":\"" + PIXEL.getToken() + "\""),
                        containsString("\"title\":\"Hoá đơn sắp đến hạn\""),
                        containsString("\"type\":\"BILL_DUE_SOON\""),
                        containsString("\"referenceType\":\"BILL\""),
                        containsString("\"channel_id\":\"default\""),
                        containsString("\"aps\":{\"sound\":\"default\"}"))))
                .andRespond(withSuccess("{\"name\":\"projects/finance-demo/messages/1\"}",
                        MediaType.APPLICATION_JSON));
        fcm.expect(requestTo(SEND_URL)).andExpect(content().string(containsString(IPHONE.getToken())))
                .andRespond(withSuccess("{\"name\":\"projects/finance-demo/messages/2\"}",
                        MediaType.APPLICATION_JSON));

        sender.send(notification());

        google.verify();
        fcm.verify();
        String[] jwt = assertion.get().split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"iss\":\"push@finance-demo.iam.gserviceaccount.com\"",
                "\"scope\":\"https://www.googleapis.com/auth/firebase.messaging\"", "\"aud\":\"" + TOKEN_URL + "\"",
                "\"iat\":" + NOW.getEpochSecond(), "\"exp\":" + (NOW.getEpochSecond() + 3600));
        Signature rsa = Signature.getInstance("SHA256withRSA");
        rsa.initVerify(keys.getPublic());
        rsa.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(rsa.verify(Base64.getUrlDecoder().decode(jwt[2]))).as("RS256 signature").isTrue();
    }

    @Test
    void forgetsUninstalledAppsButKeepsDevicesOnOtherErrors() {
        accessToken(once());
        fcm.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"code":404,"message":"Requested entity was not found.","status":"NOT_FOUND",
                         "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError",
                                     "errorCode":"UNREGISTERED"}]}}"""));
        fcm.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        sender.send(notification());

        fcm.verify();
        verify(devices).delete(PIXEL);
        verify(devices, never()).delete(IPHONE);
    }

    @Test
    void anExpiredAccessTokenIsRenewedOnce() {
        when(devices.findByOwnerIdAndProvider(OWNER, PushProvider.FCM)).thenReturn(List.of(PIXEL));
        accessToken(twice());
        fcm.expect(requestTo(SEND_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        fcm.expect(requestTo(SEND_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender.send(notification());

        google.verify();
        fcm.verify();
        verify(devices, never()).delete(ArgumentMatchers.any());
    }

    @Test
    void rejectsAFileThatIsNotAServiceAccountKeyAndNeverPrintsTheKey() {
        assertThatThrownBy(() -> ServiceAccount.parse("{\"type\":\"authorized_user\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ServiceAccount.parse(serviceAccountJson().replace("BEGIN PRIVATE KEY-----\\n",
                "BEGIN PRIVATE KEY-----\\n!!")))
                .hasMessageContaining("PKCS#8");
        assertThat(account.toString()).doesNotContain("PRIVATE").contains("finance-demo");
    }

    private void accessToken(org.springframework.test.web.client.ExpectedCount count) {
        google.expect(count, requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"ya29.token\",\"expires_in\":3599}",
                        MediaType.APPLICATION_JSON));
    }

    private String serviceAccountJson() {
        // as in Google's key files: PEM lines joined by the JSON escape \n
        String pem = "-----BEGIN PRIVATE KEY-----\\n"
                + Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded()).replace("\r\n", "\\n")
                + "\\n-----END PRIVATE KEY-----\\n";
        return """
                {"type":"service_account","project_id":"finance-demo","private_key_id":"key-1",
                 "private_key":"%s","client_email":"push@finance-demo.iam.gserviceaccount.com",
                 "token_uri":"%s"}""".formatted(pem, TOKEN_URL);
    }

    private static String formValue(String form, String name) {
        for (String pair : form.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError(name + " not in " + form);
    }

    private static Notification notification() {
        return new Notification(OWNER, NotificationType.BILL_DUE_SOON, "Hoá đơn sắp đến hạn", "Tiền điện", "BILL",
                UUID.randomUUID(), NOW);
    }
}
