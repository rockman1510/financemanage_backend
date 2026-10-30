package com.financemanage.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Opaque refresh token; only its SHA-256 hash is stored. Rotated on every use: the successor keeps the
 * {@code sessionId}, so one session = one signed-in device, whatever the number of rotations.
 */
@Entity
@Table(name = "refresh_token", schema = "identity")
public class RefreshToken {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "device_name")
    private String deviceName;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean revoked;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, UUID sessionId, String tokenHash, String deviceName, Instant createdAt,
                        Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.deviceName = deviceName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now) {
        return !revoked && now.isBefore(expiresAt);
    }

    public void revoke() {
        this.revoked = true;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSessionId() { return sessionId; }
    public String getTokenHash() { return tokenHash; }
    public String getDeviceName() { return deviceName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
}
