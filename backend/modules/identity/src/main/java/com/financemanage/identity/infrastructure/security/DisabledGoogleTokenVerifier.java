package com.financemanage.identity.infrastructure.security;

import com.financemanage.identity.application.GoogleTokenVerifier;
import com.financemanage.shared.domain.DomainException;

/** Used while no Google OAuth client id is configured. */
class DisabledGoogleTokenVerifier implements GoogleTokenVerifier {

    @Override
    public GoogleIdentity verify(String idToken) {
        throw new DomainException("GOOGLE_SIGN_IN_DISABLED", "Google sign-in is not configured on this server");
    }
}
