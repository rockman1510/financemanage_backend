package com.financemanage.reporting.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BuiltOwnerJpaRepository extends JpaRepository<BuiltOwnerJpaEntity, UUID> {
}
