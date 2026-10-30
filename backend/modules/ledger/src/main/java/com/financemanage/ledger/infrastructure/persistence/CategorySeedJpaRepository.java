package com.financemanage.ledger.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CategorySeedJpaRepository extends JpaRepository<CategorySeedJpaEntity, UUID> {
}
