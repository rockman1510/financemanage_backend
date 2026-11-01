package com.financemanage.saving.api;

import com.financemanage.shared.domain.Money;
import java.util.List;
import java.util.UUID;

/** Read-only facade used by other modules (reports: net worth). */
public interface SavingQueryApi {

    /** Money saved in each of the owner's goals. */
    List<SavedAmount> savedAmounts(UUID ownerId);

    record SavedAmount(UUID goalId, String name, Money saved) {
    }
}
