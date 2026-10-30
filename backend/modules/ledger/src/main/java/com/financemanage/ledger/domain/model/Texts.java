package com.financemanage.ledger.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;

/** Small validation helpers shared by the ledger aggregates. */
final class Texts {

    private Texts() {
    }

    /** Trimmed text, or {@code null} when blank – so an empty string clears an optional field. */
    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String required(String value, int maxLength, String code, String label) {
        String text = optional(value);
        if (text == null || text.length() > maxLength) {
            throw new DomainException(code, label + " is required and at most " + maxLength + " characters");
        }
        return text;
    }

    static Money positiveAmount(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new DomainException("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        return precise(amount);
    }

    static Money precise(Money amount) {
        if (!amount.hasMinorUnitPrecision()) {
            throw new DomainException("INVALID_AMOUNT",
                    "Amount has more decimals than " + amount.currencyCode() + " allows");
        }
        return amount;
    }
}
