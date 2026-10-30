package com.financemanage.notification.application;

import com.financemanage.identity.api.IdentityEvents.UserProfileChanged;
import com.financemanage.ledger.api.LedgerEvents.TransactionRecorded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Keeps what the daily reminder needs up to date: the user's time zone and the last day something was recorded. */
@Component
class NotificationListeners {

    private final PreferencesService preferences;

    NotificationListeners(PreferencesService preferences) {
        this.preferences = preferences;
    }

    @ApplicationModuleListener
    void on(TransactionRecorded event) {
        if (event.transaction().source() == null) { // entries the user made, not ones another module requested
            preferences.recordedSomething(event.transaction().ownerId());
        }
    }

    @ApplicationModuleListener
    void on(UserProfileChanged event) {
        preferences.timeZoneChanged(event.userId(), event.timeZone());
    }
}
