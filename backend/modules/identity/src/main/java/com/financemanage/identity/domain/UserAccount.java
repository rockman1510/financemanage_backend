package com.financemanage.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Identity is a supporting subdomain, so the JPA entity doubles as the domain model. */
@Entity
@Table(name = "user_account", schema = "identity")
public class UserAccount {

    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {
    }

    public UserAccount(String email, String passwordHash, String fullName) {
        this.id = UUID.randomUUID();
        this.email = normalize(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName.trim();
        this.createdAt = Instant.now();
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Instant getCreatedAt() { return createdAt; }
}
