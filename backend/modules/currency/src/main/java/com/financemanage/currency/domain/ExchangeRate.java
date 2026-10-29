package com.financemanage.currency.domain;

import com.financemanage.shared.domain.DomainException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * One unit of {@code base} is worth {@code rate} units of {@code quote} from {@code effectiveDate} on.
 * A rate without owner is global (fetched by a provider) and visible to every user.
 */
public class ExchangeRate {

    public static final int RATE_SCALE = 10;
    public static final String MANUAL_SOURCE = "MANUAL";

    private final UUID id;
    private final UUID ownerId;
    private final Currency base;
    private final Currency quote;
    private BigDecimal rate;
    private final LocalDate effectiveDate;
    private String source;

    public ExchangeRate(UUID id, UUID ownerId, Currency base, Currency quote, BigDecimal rate, LocalDate effectiveDate,
                        String source) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = ownerId;
        this.base = Objects.requireNonNull(base);
        this.quote = Objects.requireNonNull(quote);
        this.effectiveDate = Objects.requireNonNull(effectiveDate);
        this.source = Objects.requireNonNull(source);
        if (base.equals(quote)) {
            throw new DomainException("SAME_CURRENCY", "Base and quote currency must differ");
        }
        changeRate(rate);
    }

    public static ExchangeRate manual(UUID ownerId, Currency base, Currency quote, BigDecimal rate,
                                      LocalDate effectiveDate) {
        return new ExchangeRate(UUID.randomUUID(), Objects.requireNonNull(ownerId), base, quote, rate, effectiveDate,
                MANUAL_SOURCE);
    }

    public static ExchangeRate global(String source, Currency base, Currency quote, BigDecimal rate,
                                      LocalDate effectiveDate) {
        return new ExchangeRate(UUID.randomUUID(), null, base, quote, rate, effectiveDate, source);
    }

    public void changeRate(BigDecimal newRate) {
        if (newRate == null || newRate.signum() <= 0) {
            throw new DomainException("INVALID_RATE", "Exchange rate must be greater than zero");
        }
        this.rate = newRate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    public void changeSource(String newSource) {
        this.source = Objects.requireNonNull(newSource);
    }

    /** Rate of the opposite direction (quote -> base). */
    public BigDecimal inverse() {
        return BigDecimal.ONE.divide(rate, MathContext.DECIMAL128).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    public boolean isGlobal() {
        return ownerId == null;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Currency getBase() { return base; }
    public Currency getQuote() { return quote; }
    public BigDecimal getRate() { return rate; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public String getSource() { return source; }
}
