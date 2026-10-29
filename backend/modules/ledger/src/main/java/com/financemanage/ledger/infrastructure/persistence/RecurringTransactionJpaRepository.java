package com.financemanage.ledger.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RecurringTransactionJpaRepository extends JpaRepository<RecurringTransactionJpaEntity, UUID> {

    Optional<RecurringTransactionJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<RecurringTransactionJpaEntity> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    @Query("""
            select r.id from RecurringTransactionJpaEntity r
            where r.active = true and r.nextOccurrence <= :date order by r.nextOccurrence""")
    List<UUID> findDueIds(LocalDate date);

    boolean existsByCategoryId(UUID categoryId);

    @Modifying
    @Query("delete from RecurringTransactionJpaEntity r where r.walletId = :walletId")
    void deleteByWalletId(UUID walletId);
}
