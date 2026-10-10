package com.financemanage.shared.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the user id from the access token ({@code sub} claim). Works for local JWT and Keycloak alike. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
