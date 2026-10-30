package com.financemanage.currency.application;

import com.financemanage.currency.domain.ExchangeRateRepository;
import com.financemanage.identity.api.IdentityEvents.UserDeleted;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Deletes the exchange rates of a deleted account (after the deletion commits; retried through the outbox). */
@Component
class CurrencyAccountDeletionListener {

    private final ExchangeRateRepository rates;

    CurrencyAccountDeletionListener(ExchangeRateRepository rates) {
        this.rates = rates;
    }

    @ApplicationModuleListener
    void on(UserDeleted event) {
        rates.deleteByOwner(event.userId());
    }
}
