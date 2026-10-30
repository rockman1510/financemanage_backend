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

    /** Session (signed-in device) of the access token ({@code sid} claim); {@code null} when the token has none. */
    public static UUID sessionId(Jwt jwt) {
        String sid = jwt.getClaimAsString("sid");
        return sid == null ? null : UUID.fromString(sid);
    }
}
