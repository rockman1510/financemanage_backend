package com.financemanage.identity.infrastructure.security;

import com.financemanage.identity.application.GoogleTokenVerifier;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies Google ID tokens locally: RS256 signature against Google's published keys (fetched and cached by
 * Nimbus), issuer, audience = one of our OAuth client ids, expiry, and a verified email. Free – no Google API call
 * per sign-in.
 */
class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {

    static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final NimbusJwtDecoder decoder;

    NimbusGoogleTokenVerifier(List<String> clientIds) {
        this(NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI).build(), clientIds);
    }

    NimbusGoogleTokenVerifier(NimbusJwtDecoder decoder, List<String> clientIds) {
        this.decoder = decoder;
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtClaimValidator<String>(JwtClaimNames.ISS, ISSUERS::contains),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.stream().anyMatch(clientIds::contains))));
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid Google ID token");
        }
        return identity(jwt);
    }

    static GoogleIdentity identity(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new BadCredentialsException("The Google account has no verified email");
        }
        return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"));
    }
}
