package com.financemanage.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Two-factor recovery code for when the authenticator app is lost; usable once, only its hash is stored. */
@Entity
@Table(name = "recovery_code", schema = "identity")
public class RecoveryCode {

    @Id
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "code_hash", nullable = false)
    private String codeHash;
    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCode() {
    }

    public RecoveryCode(UUID userId, String codeHash) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public void use(Instant now) {
        usedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public Instant getUsedAt() { return usedAt; }
}
