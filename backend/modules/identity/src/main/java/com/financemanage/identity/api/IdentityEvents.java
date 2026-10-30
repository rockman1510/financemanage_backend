package com.financemanage.identity.api;

import java.util.UUID;

/** Domain events published by the identity module. Primitive payloads keep them easy to serialize (Kafka-ready). */
public final class IdentityEvents {

    private IdentityEvents() {
    }

    public record UserRegistered(UUID userId, String email, String fullName) {
    }
}
