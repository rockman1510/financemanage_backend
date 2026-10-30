package com.financemanage.investment.domain;

import com.financemanage.investment.domain.model.Asset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface AssetRepository {

    Asset save(Asset asset);

    Optional<Asset> findByIdAndOwner(UUID id, UUID ownerId);

    /**
     * Stores the ledger's answer on a PENDING trade without touching the asset (and its version), so that the
     * answer never conflicts with what the user does to the asset meanwhile.
     */
    void cashAnswered(UUID tradeId, boolean recorded, UUID transactionId);

    List<Asset> findByOwner(UUID ownerId);

    /** Assets with a symbol and units held, whose price a provider may know. */
    List<Asset> findHeldWithSymbol();

    void delete(Asset asset);

    void deleteByOwner(UUID ownerId);
}
