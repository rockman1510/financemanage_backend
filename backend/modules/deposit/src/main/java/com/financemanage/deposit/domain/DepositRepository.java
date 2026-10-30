package com.financemanage.deposit.domain;

import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.deposit.domain.model.DepositStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface DepositRepository {

    Deposit save(Deposit deposit);

    Optional<Deposit> findByIdAndOwner(UUID id, UUID ownerId);

    List<Deposit> findByOwner(UUID ownerId, DepositStatus status);

    List<Deposit> findActiveMaturingOnOrBefore(LocalDate date);

    List<Deposit> findActiveMaturingOn(LocalDate date);

    void delete(Deposit deposit);

    /** Removes every deposit of the owner (account deletion). */
    void deleteByOwner(UUID ownerId);
}
