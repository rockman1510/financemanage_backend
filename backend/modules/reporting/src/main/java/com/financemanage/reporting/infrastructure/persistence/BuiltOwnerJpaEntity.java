package com.financemanage.reporting.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Owners whose entries were loaded from the ledger once; later changes arrive as events. */
@Entity
@Table(name = "built_owner", schema = "reporting")
class BuiltOwnerJpaEntity {

    @Id
    @Column(name = "owner_id")
    UUID ownerId;
    @Column(name = "built_at", nullable = false)
    Instant builtAt;
}
