package com.financemanage.identity.application;

import java.time.Instant;
import java.util.UUID;

/** A signed-in device; {@code lastActiveAt} is when its token was last issued or refreshed. */
public record SessionView(UUID id, String deviceName, Instant lastActiveAt, Instant expiresAt, boolean current) {
}
