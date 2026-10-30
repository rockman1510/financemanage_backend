package com.financemanage.deposit.infrastructure.persistence;

import com.financemanage.deposit.domain.model.DepositStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface DepositJpaRepository extends JpaRepository<DepositJpaEntity, UUID> {

    @EntityGraph(attributePaths = "transactions")
    Optional<DepositJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<DepositJpaEntity> findByOwnerIdOrderByOpenDateDesc(UUID ownerId);

    List<DepositJpaEntity> findByOwnerIdAndStatusOrderByOpenDateDesc(UUID ownerId, DepositStatus status);

    List<DepositJpaEntity> findByStatusAndMaturityDateLessThanEqual(DepositStatus status, LocalDate date);

    List<DepositJpaEntity> findByStatusAndMaturityDate(DepositStatus status, LocalDate date);

    /** Transactions go with their deposit (ON DELETE CASCADE). */
    @Modifying
    @Query("delete from DepositJpaEntity d where d.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
