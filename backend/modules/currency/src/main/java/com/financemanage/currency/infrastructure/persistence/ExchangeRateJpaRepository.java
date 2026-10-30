package com.financemanage.currency.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ExchangeRateJpaRepository extends JpaRepository<ExchangeRateJpaEntity, UUID> {

    Optional<ExchangeRateJpaEntity> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<ExchangeRateJpaEntity> findByOwnerIdAndBaseCurrencyAndQuoteCurrencyAndEffectiveDate(
            UUID ownerId, String base, String quote, LocalDate date);

    Optional<ExchangeRateJpaEntity> findByOwnerIdIsNullAndBaseCurrencyAndQuoteCurrencyAndEffectiveDate(
            String base, String quote, LocalDate date);

    @Query("""
            select r from ExchangeRateJpaEntity r where r.ownerId = :ownerId and r.baseCurrency = :base
            and r.quoteCurrency = :quote and r.effectiveDate <= :date order by r.effectiveDate desc""")
    List<ExchangeRateJpaEntity> findLatestOwn(UUID ownerId, String base, String quote, LocalDate date, Limit limit);

    @Query("""
            select r from ExchangeRateJpaEntity r where r.ownerId is null and r.baseCurrency = :base
            and r.quoteCurrency = :quote and r.effectiveDate <= :date order by r.effectiveDate desc""")
    List<ExchangeRateJpaEntity> findLatestGlobal(String base, String quote, LocalDate date, Limit limit);

    @Modifying
    @Query("delete from ExchangeRateJpaEntity r where r.ownerId = :ownerId")
    void deleteByOwnerId(UUID ownerId);

    @Query("""
            select r from ExchangeRateJpaEntity r where r.ownerId = :ownerId or r.ownerId is null
            order by r.effectiveDate desc, r.baseCurrency, r.quoteCurrency""")
    List<ExchangeRateJpaEntity> findVisible(UUID ownerId, Limit limit);

    @Query("""
            select r from ExchangeRateJpaEntity r where (r.ownerId = :ownerId or r.ownerId is null)
            and r.baseCurrency = :base and r.quoteCurrency = :quote
            order by r.effectiveDate desc""")
    List<ExchangeRateJpaEntity> findVisiblePair(UUID ownerId, String base, String quote, Limit limit);
}
