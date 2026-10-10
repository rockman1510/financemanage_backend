package com.financemanage.shared.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/** A percentage such as an annual interest rate: {@code Percentage.of("6.5")} means 6.5%. */
public record Percentage(BigDecimal value) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Percentage {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new DomainException("INVALID_PERCENTAGE", "Percentage must not be negative");
        }
    }

    public static Percentage of(String value) {
        return new Percentage(new BigDecimal(value));
    }

    public static Percentage of(BigDecimal value) {
        return new Percentage(value);
    }

    public static Percentage zero() {
        return new Percentage(BigDecimal.ZERO);
    }

    /** 6.5% -> 0.065 */
    public BigDecimal asFraction() {
        return value.divide(HUNDRED, MathContext.DECIMAL128);
    }

    /** Value without trailing zeros and never in exponent notation, e.g. 6.5000 -> 6.5, 10.0000 -> 10. */
    public BigDecimal normalized() {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }
}
