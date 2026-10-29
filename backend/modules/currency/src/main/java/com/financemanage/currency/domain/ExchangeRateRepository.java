package com.financemanage.currency.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. A {@code null} owner means global rates. */
public interface ExchangeRateRepository {

    ExchangeRate save(ExchangeRate rate);

    Optional<ExchangeRate> findByIdAndOwner(UUID id, UUID ownerId);

    /** Rate of the same owner (or global), pair and effective date – used to update instead of duplicating. */
    Optional<ExchangeRate> findExact(UUID ownerId, String base, String quote, LocalDate effectiveDate);

    /** Most recent rate of the owner (or global) effective on or before {@code date}. */
    Optional<ExchangeRate> findLatest(UUID ownerId, String base, String quote, LocalDate date);

    /** The owner's and the global rates, newest first; {@code base}/{@code quote} may be null (no filter). */
    List<ExchangeRate> findVisible(UUID ownerId, String base, String quote, int limit);

    void delete(ExchangeRate rate);
}
