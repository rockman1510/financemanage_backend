package com.financemanage.currency.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Outbound port for an external source of exchange rates (e.g. a bank's daily rate table). Register an
 * implementation as a bean and the refresh job stores its rates as global rates – no other code changes.
 */
public interface ExchangeRateProvider {

    /** Short name stored as the rate's source, e.g. "VCB". */
    String name();

    List<ProvidedRate> fetch(LocalDate date);

    /** One unit of {@code base} is worth {@code rate} units of {@code quote}. */
    record ProvidedRate(String base, String quote, BigDecimal rate) {
    }
}
