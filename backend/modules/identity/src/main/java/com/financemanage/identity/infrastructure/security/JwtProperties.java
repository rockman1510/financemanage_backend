package com.financemanage.identity.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret          HMAC secret (at least 32 bytes) used to sign local access tokens
 * @param issuer          {@code iss} claim
 * @param accessTokenTtl  lifetime of access tokens
 */
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("app.security.jwt.secret must be at least 32 characters");
        }
        issuer = issuer == null ? "financemanage" : issuer;
        accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
    }
}
