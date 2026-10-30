package com.financemanage.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtTokenIssuerTest {

    private final SecurityConfig config = new SecurityConfig();
    private final JwtProperties properties =
            new JwtProperties("0123456789abcdef0123456789abcdef", "test-issuer", Duration.ofMinutes(5));

    @Test
    void issuedTokenCanBeDecodedWithUserIdAsSubject() {
        var key = config.jwtSigningKey(properties);
        var issuer = new JwtTokenIssuer(config.jwtEncoder(key), properties, Clock.systemUTC());
        UserAccount user = new UserAccount("an@example.com", "x", "An");

        UUID sessionId = UUID.randomUUID();
        Jwt jwt = config.jwtDecoder(key).decode(issuer.issueAccessToken(user, sessionId));

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("an@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("test-issuer");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sessionId.toString());
        assertThat(issuer.accessTokenTtlSeconds()).isEqualTo(300);
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("short", null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
