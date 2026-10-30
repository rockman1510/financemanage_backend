package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.TransactionRepository;
import com.financemanage.ledger.domain.model.PageResult;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.TransactionFilter;
import com.financemanage.ledger.domain.model.TransactionTotals;
import com.financemanage.ledger.domain.model.TransactionType;
import com.financemanage.shared.domain.Money;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link Transaction} to its JPA entity. */
@Repository
class TransactionRepositoryAdapter implements TransactionRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredOn"), Sort.Order.desc("createdAt"));

    private final TransactionJpaRepository jpa;
    private final EntityManager entityManager;

    TransactionRepositoryAdapter(TransactionJpaRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    public Transaction save(Transaction t) {
        TransactionJpaEntity e;
        if (t.getVersion() == null) {
            e = new TransactionJpaEntity();
            e.id = t.getId();
            e.createdAt = t.getCreatedAt();
        } else {
            e = jpa.findById(t.getId())
                    .orElseThrow(() -> new OptimisticLockingFailureException("Transaction was deleted"));
            if (!Objects.equals(e.version, t.getVersion())) {
                throw new OptimisticLockingFailureException("Transaction " + t.getId() + " was modified");
            }
        }
        e.ownerId = t.getOwnerId();
        e.walletId = t.getWalletId();
        e.type = t.getType();
        e.amount = t.getAmount().amount();
        e.currency = t.getAmount().currencyCode();
        e.categoryId = t.getCategoryId();
        e.occurredOn = t.getOccurredOn();
        e.note = t.getNote();
        e.withPerson = t.getWithPerson();
        e.excludeFromReport = t.isExcludeFromReport();
        e.transferId = t.getTransferId();
        e.counterpartWalletId = t.getCounterpartWalletId();
        e.exchangeRate = t.getExchangeRate();
        e.recurringId = t.getRecurringId();
        e.idempotencyKey = t.getIdempotencyKey();
        e.updatedAt = Instant.now();
        return toDomain(jpa.saveAndFlush(e));
    }

    @Override
    public Optional<Transaction> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(UUID ownerId, String idempotencyKey) {
        return jpa.findByOwnerIdAndIdempotencyKey(ownerId, idempotencyKey).map(this::toDomain);
    }

    @Override
    public List<Transaction> findByTransfer(UUID ownerId, UUID transferId) {
        // TRANSFER_OUT sorts after TRANSFER_IN alphabetically, so descending puts the outgoing leg first
        return jpa.findByOwnerIdAndTransferIdOrderByTypeDesc(ownerId, transferId).stream().map(this::toDomain)
                .toList();
    }

    @Override
    public PageResult<Transaction> search(TransactionFilter filter, int page, int size) {
        Page<TransactionJpaEntity> result = jpa.findAll(TransactionSpecifications.matching(filter),
                PageRequest.of(page, size, NEWEST_FIRST));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements());
    }

    @Override
    public List<TransactionTotals> totals(TransactionFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<TransactionJpaEntity> root = query.from(TransactionJpaEntity.class);
        query.select(cb.tuple(root.get("currency"), root.get("type"), cb.sum(root.<BigDecimal>get("amount"))))
                .where(TransactionSpecifications.matching(filter).toPredicate(root, query, cb),
                        cb.isFalse(root.get("excludeFromReport")),
                        root.get("type").in(TransactionType.INCOME, TransactionType.EXPENSE))
                .groupBy(root.get("currency"), root.get("type"));

        Map<String, BigDecimal[]> sums = new TreeMap<>(); // currency -> [income, expense]
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            BigDecimal[] pair = sums.computeIfAbsent(row.get(0, String.class),
                    c -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            int index = row.get(1, TransactionType.class) == TransactionType.INCOME ? 0 : 1;
            pair[index] = pair[index].add(row.get(2, BigDecimal.class));
        }
        return sums.entrySet().stream().map(e -> {
            Currency currency = Currency.getInstance(e.getKey());
            return new TransactionTotals(new Money(e.getValue()[0], currency), new Money(e.getValue()[1], currency));
        }).toList();
    }

    @Override
    public boolean existsByWallet(UUID walletId) {
        return jpa.existsByWalletId(walletId);
    }

    @Override
    public boolean existsByCategory(UUID categoryId) {
        return jpa.existsByCategoryId(categoryId);
    }

    @Override
    public void delete(Transaction transaction) {
        jpa.deleteById(transaction.getId());
        jpa.flush();
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteByOwnerId(ownerId);
    }

    private Transaction toDomain(TransactionJpaEntity e) {
        return new Transaction(e.id, e.ownerId, e.walletId, e.type, Money.of(e.amount, e.currency), e.categoryId,
                e.occurredOn, e.note, e.withPerson, e.excludeFromReport, e.transferId, e.counterpartWalletId,
                e.exchangeRate, e.recurringId, e.idempotencyKey, e.createdAt, e.version);
    }
}
