package com.financemanage.currency.application;

import com.financemanage.currency.domain.ExchangeRate;
import com.financemanage.currency.domain.ExchangeRateRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Test double for the outbound port – keeps service tests fast and free of a database. */
class InMemoryExchangeRateRepository implements ExchangeRateRepository {

    final Map<UUID, ExchangeRate> store = new LinkedHashMap<>();

    @Override
    public ExchangeRate save(ExchangeRate rate) {
        store.put(rate.getId(), rate);
        return rate;
    }

    @Override
    public Optional<ExchangeRate> findByIdAndOwner(UUID id, UUID ownerId) {
        return Optional.ofNullable(store.get(id)).filter(r -> Objects.equals(r.getOwnerId(), ownerId));
    }

    @Override
    public Optional<ExchangeRate> findExact(UUID ownerId, String base, String quote, LocalDate effectiveDate) {
        return pair(ownerId, base, quote).filter(r -> r.getEffectiveDate().equals(effectiveDate)).findFirst();
    }

    @Override
    public Optional<ExchangeRate> findLatest(UUID ownerId, String base, String quote, LocalDate date) {
        return pair(ownerId, base, quote).filter(r -> !r.getEffectiveDate().isAfter(date))
                .max(Comparator.comparing(ExchangeRate::getEffectiveDate));
    }

    @Override
    public List<ExchangeRate> findVisible(UUID ownerId, String base, String quote, int limit) {
        return store.values().stream()
                .filter(r -> r.getOwnerId() == null || r.getOwnerId().equals(ownerId))
                .filter(r -> base == null || r.getBase().getCurrencyCode().equals(base))
                .filter(r -> quote == null || r.getQuote().getCurrencyCode().equals(quote))
                .sorted(Comparator.comparing(ExchangeRate::getEffectiveDate).reversed())
                .limit(limit).toList();
    }

    @Override
    public void delete(ExchangeRate rate) {
        store.remove(rate.getId());
    }

    private Stream<ExchangeRate> pair(UUID ownerId, String base, String quote) {
        return store.values().stream()
                .filter(r -> Objects.equals(r.getOwnerId(), ownerId))
                .filter(r -> r.getBase().getCurrencyCode().equals(base))
                .filter(r -> r.getQuote().getCurrencyCode().equals(quote));
    }
}
