package com.financemanage.loan.infrastructure.persistence;

import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoanJpaRepository extends JpaRepository<LoanJpaEntity, UUID> {

    Optional<LoanJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            select l from LoanJpaEntity l
            where l.ownerId = :ownerId
              and (:direction is null or l.direction = :direction)
              and (:status is null or l.status = :status)
            order by l.startDate desc""")
    List<LoanJpaEntity> search(@Param("ownerId") UUID ownerId, @Param("direction") LoanDirection direction,
                               @Param("status") LoanStatus status);

    @Query("""
            select distinct l from LoanJpaEntity l join l.installments i
            where l.status = com.financemanage.loan.domain.model.LoanStatus.ACTIVE
              and i.dueDate = :date and i.settled = false""")
    List<LoanJpaEntity> findActiveWithUnpaidInstallmentDueOn(@Param("date") LocalDate date);
}
