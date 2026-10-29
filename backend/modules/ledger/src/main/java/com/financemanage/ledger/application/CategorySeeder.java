package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.model.DefaultCategories;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the default categories of a user once. Runs in its own transaction so that a concurrent seeding (the
 * {@code UserRegistered} listener racing the first category request) only fails this step, with a
 * {@code DataIntegrityViolationException} the callers ignore.
 */
@Component
public class CategorySeeder {

    private final CategoryRepository categories;

    public CategorySeeder(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seed(UUID ownerId) {
        if (categories.isSeeded(ownerId)) {
            return;
        }
        categories.markSeeded(ownerId);
        categories.saveAll(DefaultCategories.forOwner(ownerId));
    }
}
