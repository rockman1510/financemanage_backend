package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.WalletRepository;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.Money;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** Maps the framework-free {@link Wallet} aggregate to its JPA entity. */
@Repository
class WalletRepositoryAdapter implements WalletRepository {

    private final WalletJpaRepository jpa;

    WalletRepositoryAdapter(WalletJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Wallet save(Wallet wallet) {
        WalletJpaEntity entity;
        if (wallet.getVersion() == null) {
            entity = new WalletJpaEntity();
            entity.id = wallet.getId();
        } else {
            entity = jpa.findById(wallet.getId())
                    .orElseThrow(() -> new OptimisticLockingFailureException("Wallet was deleted"));
            if (!Objects.equals(entity.version, wallet.getVersion())) {
                throw new OptimisticLockingFailureException("Wallet " + wallet.getId() + " was modified");
            }
        }
        entity.ownerId = wallet.getOwnerId();
        entity.name = wallet.getName();
        entity.type = wallet.getType();
        entity.currency = wallet.currencyCode();
        entity.initialBalance = wallet.getInitialBalance().amount();
        entity.balance = wallet.getBalance().amount();
        entity.excludedFromTotal = wallet.isExcludedFromTotal();
        entity.archived = wallet.isArchived();
        entity.icon = wallet.getIcon();
        entity.creditLimit = wallet.getCreditLimit() == null ? null : wallet.getCreditLimit().amount();
        entity.statementDay = wallet.getStatementDay();
        entity.paymentDueDay = wallet.getPaymentDueDay();
        entity.updatedAt = Instant.now();
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public Optional<Wallet> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public List<Wallet> findByOwner(UUID ownerId, boolean includeArchived) {
        List<WalletJpaEntity> rows = includeArchived
                ? jpa.findByOwnerIdOrderByCreatedAtAsc(ownerId)
                : jpa.findByOwnerIdAndArchivedFalseOrderByCreatedAtAsc(ownerId);
        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(Wallet wallet) {
        jpa.deleteById(wallet.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        jpa.deleteByOwnerId(ownerId);
    }

    private Wallet toDomain(WalletJpaEntity e) {
        Currency currency = Currency.getInstance(e.currency);
        return new Wallet(e.id, e.ownerId, e.name, e.type, new Money(e.initialBalance, currency),
                new Money(e.balance, currency), e.excludedFromTotal, e.archived, e.icon,
                e.creditLimit == null ? null : new Money(e.creditLimit, currency), e.statementDay, e.paymentDueDay,
                e.version);
    }
}
