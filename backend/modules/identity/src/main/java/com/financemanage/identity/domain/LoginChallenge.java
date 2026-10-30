package com.financemanage.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Second step of a sign-in for accounts with two-factor authentication: the password (or Google) step was done,
 * the client must now send a code with this challenge. Short-lived and limited in attempts.
 */
@Entity
@Table(name = "login_challenge", schema = "identity")
public class LoginChallenge {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    @Column(name = "device_name")
    private String deviceName;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private boolean used;

    protected LoginChallenge() {
    }

    public LoginChallenge(UUID userId, String tokenHash, String deviceName, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.deviceName = deviceName;
        this.expiresAt = expiresAt;
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return !used && attempts < maxAttempts && now.isBefore(expiresAt);
    }

    public void recordFailure() {
        attempts++;
    }

    public void complete() {
        used = true;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public String getDeviceName() { return deviceName; }
    public Instant getExpiresAt() { return expiresAt; }
}
