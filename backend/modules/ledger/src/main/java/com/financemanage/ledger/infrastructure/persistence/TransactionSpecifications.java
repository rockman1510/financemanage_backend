package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.model.TransactionFilter;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Translates a {@link TransactionFilter} into a JPA criteria predicate. */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<TransactionJpaEntity> matching(TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerId"), filter.ownerId()));
            if (filter.walletId() != null) {
                predicates.add(cb.equal(root.get("walletId"), filter.walletId()));
            }
            if (!filter.categoryIds().isEmpty()) {
                predicates.add(root.get("categoryId").in(filter.categoryIds()));
            }
            if (!filter.types().isEmpty()) {
                predicates.add(root.get("type").in(filter.types()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredOn"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredOn"), filter.to()));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            if (filter.text() != null) {
                String pattern = "%" + escapeLike(filter.text().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("note")), pattern, '\\'),
                        cb.like(cb.lower(root.get("withPerson")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
