package com.financemanage.identity.api;

import java.util.UUID;

/** Domain events published by the identity module. Primitive payloads keep them easy to serialize (Kafka-ready). */
public final class IdentityEvents {

    private IdentityEvents() {
    }

    public record UserRegistered(UUID userId, String email, String fullName) {
    }

    /** Preferences other modules may use: default currency, language, time zone and the day a month starts. */
    public record UserProfileChanged(UUID userId, String defaultCurrency, String locale, String timeZone,
                                     int monthStartDay) {
    }

    /** The account was deleted; every module deletes the data it owns for this user. */
    public record UserDeleted(UUID userId) {
    }
}
