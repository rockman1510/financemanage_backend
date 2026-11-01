package com.financemanage.investment.infrastructure.persistence;

import com.financemanage.investment.domain.model.CashStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TradeJpaRepository extends JpaRepository<TradeJpaEntity, UUID> {

    @Modifying(clearAutomatically = true)
    @Query("update TradeJpaEntity t set t.cashStatus = :status, t.transactionId = :transactionId"
            + " where t.id = :id and t.cashStatus = com.financemanage.investment.domain.model.CashStatus.PENDING")
    int answer(UUID id, CashStatus status, UUID transactionId);
}
