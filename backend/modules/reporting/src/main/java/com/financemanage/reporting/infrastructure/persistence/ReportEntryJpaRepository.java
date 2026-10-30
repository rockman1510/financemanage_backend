package com.financemanage.reporting.infrastructure.persistence;

import com.financemanage.reporting.domain.ReportEntryStore.DailyTotal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ReportEntryJpaRepository extends JpaRepository<ReportEntryJpaEntity, UUID> {

    @Query("select new com.financemanage.reporting.domain.ReportEntryStore$DailyTotal(e.type, e.currency,"
            + " e.categoryId, e.date, sum(e.amount)) from ReportEntryJpaEntity e"
            + " where e.ownerId = :ownerId and e.deleted = false and e.excluded = false"
            + " and e.date between :from and :to group by e.type, e.currency, e.categoryId, e.date")
    List<DailyTotal> dailyTotals(UUID ownerId, LocalDate from, LocalDate to);

    List<ReportEntryJpaEntity> findByOwnerIdAndDeletedFalseAndDateBetweenOrderByDateAsc(UUID ownerId, LocalDate from,
                                                                                      LocalDate to);

    @Modifying
    @Query("delete from ReportEntryJpaEntity e where e.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);
}
