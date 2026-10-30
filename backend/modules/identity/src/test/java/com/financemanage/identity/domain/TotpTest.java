package com.financemanage.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** RFC 6238 appendix B: SHA-1 key "12345678901234567890". */
    private static final String RFC_SECRET =
            Totp.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfc6238TestVectors() {
        assertThat(RFC_SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        // the RFC lists 8-digit codes; 6-digit codes are their last six digits
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void acceptsTheNeighbourStepsOnlyOnce() {
        Instant now = Instant.ofEpochSecond(1111111109);
        long step = Totp.step(now);
        String previous = Totp.code(RFC_SECRET, step - 1);

        assertThat(Totp.verify(RFC_SECRET, previous, now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, previous, now, step - 1)).isEmpty(); // replay
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, null)).isEmpty(); // too old
        assertThat(Totp.verify(RFC_SECRET, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
    }

    @Test
    void base32RoundTripsAndBuildsAnOtpauthUri() {
        String secret = Totp.newSecret(new SecureRandom());

        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32Encode(Totp.base32Decode(secret))).isEqualTo(secret);
        assertThat(Totp.otpauthUri("Finance Manage", "an@example.com", "ABC"))
                .isEqualTo("otpauth://totp/Finance%20Manage:an%40example.com?secret=ABC&issuer=Finance%20Manage"
                        + "&algorithm=SHA1&digits=6&period=30");
    }
}
