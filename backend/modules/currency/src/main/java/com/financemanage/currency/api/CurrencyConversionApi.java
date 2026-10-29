package com.financemanage.currency.api;

import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Converts amounts between currencies for other modules. The owner's own rates win over global (provider) rates;
 * the latest rate effective on or before {@code asOf} is used, and an inverse pair is used when no direct one exists.
 */
public interface CurrencyConversionApi {

    /** How many units of {@code to} one unit of {@code from} is worth; 1 for the same currency. */
    Optional<BigDecimal> rate(UUID ownerId, String from, String to, LocalDate asOf);

    /** Converts and rounds to the target currency's minor unit; fails with EXCHANGE_RATE_MISSING (422). */
    Money convert(UUID ownerId, Money amount, String targetCurrency, LocalDate asOf);
}
