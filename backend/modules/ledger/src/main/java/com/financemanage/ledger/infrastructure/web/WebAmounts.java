package com.financemanage.ledger.infrastructure.web;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;

/** Helpers shared by the ledger REST adapters. */
final class WebAmounts {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private WebAmounts() {
    }

    /** Amount rounded to the currency minor unit, e.g. 50000 VND or 10.25 USD. */
    static BigDecimal plain(Money money) {
        return money == null ? null : money.roundedAmount();
    }

    /** Rate without trailing zeros and never in exponent notation, e.g. 25400.0000000000 -> 25400. */
    static BigDecimal plain(BigDecimal rate) {
        if (rate == null) {
            return null;
        }
        BigDecimal stripped = rate.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    static String idempotencyKey(String header) {
        if (header != null && (header.isBlank() || header.length() > MAX_IDEMPOTENCY_KEY_LENGTH)) {
            throw new DomainException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 1 to " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return header;
    }
}
