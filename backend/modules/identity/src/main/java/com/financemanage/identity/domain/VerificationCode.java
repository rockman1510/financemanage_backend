package com.financemanage.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A one-time code sent by email; only its hash is stored. Wrong guesses are counted and limited. */
@Entity
@Table(name = "verification_code", schema = "identity")
public class VerificationCode {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    private VerificationPurpose purpose;
    @Column(name = "code_hash", nullable = false)
    private String codeHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VerificationCode() {
    }

    public VerificationCode(UUID userId, VerificationPurpose purpose, String codeHash, Instant createdAt,
                            Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Checks a guess. A correct guess uses the code up; after {@code maxAttempts} wrong guesses the code is burnt
     * so it cannot be brute-forced.
     */
    public boolean redeem(String guessHash, Instant now, int maxAttempts) {
        if (!isUsable(now)) {
            return false;
        }
        if (codeHash.equals(guessHash)) {
            usedAt = now;
            return true;
        }
        attempts++;
        if (attempts >= maxAttempts) {
            usedAt = now;
        }
        return false;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void invalidate(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public VerificationPurpose getPurpose() { return purpose; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public Instant getUsedAt() { return usedAt; }
    public String getCodeHash() { return codeHash; }
}
