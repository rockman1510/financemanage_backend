package com.financemanage.ledger.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Search criteria for transactions; {@code null} or empty values do not filter. */
public record TransactionFilter(UUID ownerId, UUID walletId, Set<UUID> categoryIds, Set<TransactionType> types,
                                LocalDate from, LocalDate to, BigDecimal minAmount, BigDecimal maxAmount,
                                String text) {

    public TransactionFilter {
        categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
        types = types == null ? Set.of() : Set.copyOf(types);
        text = text == null || text.isBlank() ? null : text.trim();
    }

    public TransactionFilter withCategoryIds(Set<UUID> ids) {
        return new TransactionFilter(ownerId, walletId, ids, types, from, to, minAmount, maxAmount, text);
    }
}
