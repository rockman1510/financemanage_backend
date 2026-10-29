package com.financemanage.ledger.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Marks that the default categories were created for an owner; the primary key prevents seeding twice. */
@Entity
@Table(name = "category_seed", schema = "ledger")
class CategorySeedJpaEntity {

    @Id
    @Column(name = "owner_id")
    UUID ownerId;
    @Column(name = "seeded_at", nullable = false)
    Instant seededAt = Instant.now();
}
