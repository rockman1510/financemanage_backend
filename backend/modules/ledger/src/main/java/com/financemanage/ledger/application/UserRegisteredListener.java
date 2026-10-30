package com.financemanage.ledger.application;

import com.financemanage.identity.api.IdentityEvents.UserRegistered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Gives every new user the default categories (after the registration commits; retried through the outbox). */
@Component
class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final CategorySeeder seeder;

    UserRegisteredListener(CategorySeeder seeder) {
        this.seeder = seeder;
    }

    @ApplicationModuleListener
    void on(UserRegistered event) {
        try {
            seeder.seed(event.userId());
        } catch (DataIntegrityViolationException e) {
            log.debug("Default categories of user {} were created concurrently", event.userId());
        }
    }
}
