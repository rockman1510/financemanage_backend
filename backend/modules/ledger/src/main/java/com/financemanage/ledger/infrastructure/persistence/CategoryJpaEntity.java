package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.model.CategoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category", schema = "ledger")
class CategoryJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(nullable = false)
    String name;
    @Enumerated(EnumType.STRING)
    CategoryType type;
    @Column(name = "parent_id")
    UUID parentId;
    String icon;
    String code;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
}
