package com.financemanage.shared.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary amount. All arithmetic is done with {@link BigDecimal} at {@link #CALC_SCALE}
 * decimals; {@link #rounded()} rounds to the currency's minor unit (0 for VND, 2 for USD).
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money>, Serializable {

    public static final int CALC_SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(CALC_SCALE, ROUNDING);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, currency(currencyCode));
    }

    /** Resolves an ISO 4217 code; an unknown code is a business error (HTTP 422) rather than a server error. */
    public static Currency currency(String currencyCode) {
        try {
            return Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DomainException("INVALID_CURRENCY", "Unknown currency " + currencyCode);
        }
    }

    public static Money of(String amount, String currencyCode) {
        return of(new BigDecimal(amount), currencyCode);
    }

    public static Money of(long amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        assertSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        assertSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }

    public Money dividedBy(int divisor) {
        return new Money(amount.divide(BigDecimal.valueOf(divisor), CALC_SCALE, ROUNDING), currency);
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    /** True when the amount has no more decimals than the currency allows, e.g. 1000 VND or 10.25 USD. */
    public boolean hasMinorUnitPrecision() {
        return amount.stripTrailingZeros().scale() <= Math.max(currency.getDefaultFractionDigits(), 0);
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    /** Rounds to the currency minor unit, e.g. 1234.5678 VND -> 1235. */
    public Money rounded() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        return new Money(amount.setScale(digits, ROUNDING), currency);
    }

    /** Amount rounded to the currency minor unit with that exact scale, e.g. 1235 VND or 10.01 USD (for APIs). */
    public BigDecimal roundedAmount() {
        return rounded().amount().setScale(Math.max(currency.getDefaultFractionDigits(), 0), ROUNDING);
    }

    /** Rounds towards zero to the currency minor unit; used when splitting so the last part absorbs the rest. */
    public Money roundedDown() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        return new Money(amount.setScale(digits, RoundingMode.DOWN), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public String currencyCode() {
        return currency.getCurrencyCode();
    }

    @Override
    public int compareTo(Money other) {
        assertSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void assertSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new DomainException("CURRENCY_MISMATCH",
                    "Cannot combine " + currency + " with " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.stripTrailingZeros().toPlainString() + " " + currency.getCurrencyCode();
    }
}
