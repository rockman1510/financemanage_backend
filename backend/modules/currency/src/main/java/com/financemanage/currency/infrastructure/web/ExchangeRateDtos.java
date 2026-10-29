package com.financemanage.currency.infrastructure.web;

import com.financemanage.currency.domain.ExchangeRate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request/response contracts of the exchange rate REST API (v1). */
final class ExchangeRateDtos {

    private ExchangeRateDtos() {
    }

    record SaveExchangeRateRequest(
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String base,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String quote,
            @NotNull @Positive BigDecimal rate,
            LocalDate effectiveDate) {
    }

    record ExchangeRateResponse(UUID id, String base, String quote, BigDecimal rate, LocalDate effectiveDate,
                                String source, boolean global) {

        static ExchangeRateResponse from(ExchangeRate r) {
            return new ExchangeRateResponse(r.getId(), r.getBase().getCurrencyCode(), r.getQuote().getCurrencyCode(),
                    plain(r.getRate()), r.getEffectiveDate(), r.getSource(), r.isGlobal());
        }
    }

    record ConversionResponse(BigDecimal amount, String from, String to, BigDecimal rate, BigDecimal converted,
                              LocalDate date) {
    }

    /** Rate without trailing zeros and never in exponent notation, e.g. 25400.0000000000 -> 25400. */
    static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
