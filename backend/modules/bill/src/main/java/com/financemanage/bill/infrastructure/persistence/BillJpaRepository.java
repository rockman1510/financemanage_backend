package com.financemanage.bill.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BillJpaRepository extends JpaRepository<BillJpaEntity, UUID> {

    @EntityGraph(attributePaths = "payments")
    Optional<BillJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select p.bill from BillPaymentJpaEntity p where p.id = :paymentId")
    Optional<BillJpaEntity> findByPaymentId(UUID paymentId);

    List<BillJpaEntity> findByOwnerId(UUID ownerId);

    List<BillJpaEntity> findByActiveTrueAndNextDueDateBetween(LocalDate from, LocalDate to);
}
