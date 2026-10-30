package com.financemanage.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.identity.application.GoogleTokenVerifier.GoogleIdentity;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs tokens with a local RSA key standing in for Google's, to check every validation rule. */
class NimbusGoogleTokenVerifierTest {

    private NimbusJwtEncoder encoder;
    private NimbusGoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("k1").build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        verifier = new NimbusGoogleTokenVerifier(
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build(), List.of("web-client",
                        "android-client"));
    }

    @Test
    void acceptsAValidToken() {
        GoogleIdentity identity = verifier.verify(token(c -> { }));

        assertThat(identity).isEqualTo(new GoogleIdentity("google-123", "an@gmail.com", "An"));
    }

    @Test
    void rejectsWrongAudienceIssuerExpiryOrUnverifiedEmail() {
        assertThatThrownBy(() -> verifier.verify(token(c -> c.audience(List.of("someone-else")))))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> verifier.verify(token(c -> c.issuer("https://evil.example.com"))))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> verifier.verify(token(c -> c.issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))))).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> verifier.verify(token(c -> c.claim("email_verified", false))))
                .isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(BadCredentialsException.class);
    }

    private String token(Consumer<JwtClaimsSet.Builder> customizer) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("https://accounts.google.com")
                .audience(List.of("android-client"))
                .subject("google-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", "an@gmail.com")
                .claim("email_verified", true)
                .claim("name", "An");
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("k1").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
