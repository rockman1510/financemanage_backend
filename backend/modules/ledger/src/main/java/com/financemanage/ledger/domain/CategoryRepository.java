package com.financemanage.ledger.domain;

import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface CategoryRepository {

    Category save(Category category);

    void saveAll(List<Category> categories);

    Optional<Category> findByIdAndOwner(UUID id, UUID ownerId);

    /** The owner's categories, parents before children; {@code type} may be null (all types). */
    List<Category> findByOwner(UUID ownerId, CategoryType type);

    boolean hasChildren(UUID categoryId);

    void delete(Category category);

    /** Whether the default categories were already created for the owner. */
    boolean isSeeded(UUID ownerId);

    /** Records that the defaults were created; fails when another transaction did it concurrently. */
    void markSeeded(UUID ownerId);
}
