package com.financemanage.deposit.application;

import com.financemanage.deposit.domain.DepositRepository;
import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.deposit.domain.model.DepositStatus;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test double for the outbound port – keeps service tests fast and free of a database. */
class InMemoryDepositRepository implements DepositRepository {

    final Map<UUID, Deposit> store = new LinkedHashMap<>();

    @Override
    public Deposit save(Deposit deposit) {
        store.put(deposit.getId(), deposit);
        return deposit;
    }

    @Override
    public Optional<Deposit> findByIdAndOwner(UUID id, UUID ownerId) {
        return Optional.ofNullable(store.get(id)).filter(d -> d.getOwnerId().equals(ownerId));
    }

    @Override
    public Optional<Deposit> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Deposit> findByOwner(UUID ownerId, DepositStatus status) {
        return store.values().stream()
                .filter(d -> d.getOwnerId().equals(ownerId))
                .filter(d -> status == null || d.getStatus() == status)
                .toList();
    }

    @Override
    public List<Deposit> findActiveMaturingOnOrBefore(LocalDate date) {
        return store.values().stream()
                .filter(d -> d.getStatus() == DepositStatus.ACTIVE && d.getMaturityDate() != null
                        && !d.getMaturityDate().isAfter(date))
                .toList();
    }

    @Override
    public List<Deposit> findActiveMaturingOn(LocalDate date) {
        return store.values().stream()
                .filter(d -> d.getStatus() == DepositStatus.ACTIVE && date.equals(d.getMaturityDate()))
                .toList();
    }

    @Override
    public void delete(Deposit deposit) {
        store.remove(deposit.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        store.values().removeIf(d -> d.getOwnerId().equals(ownerId));
    }
}
