package com.financemanage.ledger.domain;

import com.financemanage.ledger.domain.model.Wallet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface WalletRepository {

    Wallet save(Wallet wallet);

    Optional<Wallet> findByIdAndOwner(UUID id, UUID ownerId);

    List<Wallet> findByOwner(UUID ownerId, boolean includeArchived);

    void delete(Wallet wallet);
}
