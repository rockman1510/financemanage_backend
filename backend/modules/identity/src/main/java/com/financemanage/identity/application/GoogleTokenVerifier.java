package com.financemanage.identity.application;

/** Outbound port: checks a Google ID token (signature, issuer, audience, expiry) obtained by the mobile app. */
public interface GoogleTokenVerifier {

    /**
     * @throws org.springframework.security.authentication.BadCredentialsException when the token is not valid
     * @throws com.financemanage.shared.domain.DomainException GOOGLE_SIGN_IN_DISABLED when no client id is set up
     */
    GoogleIdentity verify(String idToken);

    /** Google account: stable {@code subject}, an email Google has verified, and the display name. */
    record GoogleIdentity(String subject, String email, String name) {
    }
}
