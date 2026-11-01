package com.financemanage.notification.infrastructure.sender;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth 2.0 access tokens for a service account (the "JWT bearer" grant): a JWT signed with the account's RSA key is
 * exchanged at Google's token endpoint. A token lives one hour and is reused until a minute before it expires.
 */
class GoogleAccessTokens {

    static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final Duration LIFETIME = Duration.ofHours(1);
    private static final Duration RENEW_BEFORE = Duration.ofMinutes(1);
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();

    private final ServiceAccount account;
    private final RestClient client;
    private final Clock clock;
    private String token;
    private Instant expiresAt = Instant.MIN;

    GoogleAccessTokens(ServiceAccount account, RestClient client, Clock clock) {
        this.account = account;
        this.client = client;
        this.clock = clock;
    }

    synchronized String token() {
        Instant now = clock.instant();
        if (token == null || !now.isBefore(expiresAt.minus(RENEW_BEFORE))) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            form.add("assertion", assertion(now));
            Map<?, ?> response = client.post().uri(account.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            if (response == null || !(response.get("access_token") instanceof String accessToken)) {
                throw new IllegalStateException("Google token endpoint returned no access_token");
            }
            token = accessToken;
            Object expiresIn = response.get("expires_in");
            expiresAt = now.plusSeconds(expiresIn instanceof Number n ? n.longValue() : LIFETIME.toSeconds());
        }
        return token;
    }

    /** Clears the cached token, e.g. after the API answered 401. */
    synchronized void invalidate() {
        token = null;
    }

    String assertion(Instant now) {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\""
                + (account.privateKeyId() == null ? "" : ",\"kid\":\"" + account.privateKeyId() + "\"") + "}";
        String claims = "{\"iss\":\"" + account.clientEmail() + "\",\"scope\":\"" + FCM_SCOPE + "\",\"aud\":\""
                + account.tokenUri() + "\",\"iat\":" + now.getEpochSecond() + ",\"exp\":"
                + now.plus(LIFETIME).getEpochSecond() + "}";
        String signingInput = encode(header) + "." + encode(claims);
        try {
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(account.privateKey());
            rsa.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + BASE64URL.encodeToString(rsa.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign the service-account JWT", e);
        }
    }

    private static String encode(String json) {
        return BASE64URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
