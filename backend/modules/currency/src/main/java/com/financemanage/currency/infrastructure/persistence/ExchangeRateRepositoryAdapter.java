package com.financemanage.currency.infrastructure.persistence;

import com.financemanage.currency.domain.ExchangeRate;
import com.financemanage.currency.domain.ExchangeRateRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link ExchangeRate} to its JPA entity. */
@Repository
class ExchangeRateRepositoryAdapter implements ExchangeRateRepository {

    private final ExchangeRateJpaRepository jpa;

    ExchangeRateRepositoryAdapter(ExchangeRateJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public ExchangeRate save(ExchangeRate rate) {
        ExchangeRateJpaEntity entity = jpa.findById(rate.getId()).orElseGet(() -> {
            ExchangeRateJpaEntity created = new ExchangeRateJpaEntity();
            created.id = rate.getId();
            return created;
        });
        entity.ownerId = rate.getOwnerId();
        entity.baseCurrency = rate.getBase().getCurrencyCode();
        entity.quoteCurrency = rate.getQuote().getCurrencyCode();
        entity.rate = rate.getRate();
        entity.effectiveDate = rate.getEffectiveDate();
        entity.source = rate.getSource();
        entity.updatedAt = Instant.now();
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<ExchangeRate> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public Optional<ExchangeRate> findExact(UUID ownerId, String base, String quote, LocalDate effectiveDate) {
        return (ownerId == null
                ? jpa.findByOwnerIdIsNullAndBaseCurrencyAndQuoteCurrencyAndEffectiveDate(base, quote, effectiveDate)
                : jpa.findByOwnerIdAndBaseCurrencyAndQuoteCurrencyAndEffectiveDate(ownerId, base, quote,
                        effectiveDate))
                .map(this::toDomain);
    }

    @Override
    public Optional<ExchangeRate> findLatest(UUID ownerId, String base, String quote, LocalDate date) {
        List<ExchangeRateJpaEntity> latest = ownerId == null
                ? jpa.findLatestGlobal(base, quote, date, Limit.of(1))
                : jpa.findLatestOwn(ownerId, base, quote, date, Limit.of(1));
        return latest.stream().findFirst().map(this::toDomain);
    }

    @Override
    public List<ExchangeRate> findVisible(UUID ownerId, String base, String quote, int limit) {
        if (base != null && quote != null) {
            return jpa.findVisiblePair(ownerId, base, quote, Limit.of(limit)).stream().map(this::toDomain).toList();
        }
        return jpa.findVisible(ownerId, Limit.of(limit)).stream()
                .filter(r -> base == null || base.equals(r.baseCurrency))
                .filter(r -> quote == null || quote.equals(r.quoteCurrency))
                .map(this::toDomain).toList();
    }

    @Override
    public void delete(ExchangeRate rate) {
        jpa.deleteById(rate.getId());
    }

    private ExchangeRate toDomain(ExchangeRateJpaEntity e) {
        return new ExchangeRate(e.id, e.ownerId, Currency.getInstance(e.baseCurrency),
                Currency.getInstance(e.quoteCurrency), e.rate, e.effectiveDate, e.source);
    }
}
