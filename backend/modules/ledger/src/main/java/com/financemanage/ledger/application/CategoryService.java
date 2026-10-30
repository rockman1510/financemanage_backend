package com.financemanage.ledger.application;

import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.RecurringTransactionRepository;
import com.financemanage.ledger.domain.TransactionRepository;
import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final CategorySeeder seeder;
    private final TransactionRepository transactions;
    private final RecurringTransactionRepository recurring;

    public CategoryService(CategoryRepository categories, CategorySeeder seeder, TransactionRepository transactions,
                           RecurringTransactionRepository recurring) {
        this.categories = categories;
        this.seeder = seeder;
        this.transactions = transactions;
        this.recurring = recurring;
    }

    /**
     * The owner's categories, parents before children. Creates the defaults first when they do not exist yet (users
     * registered before the ledger module, or when the registration event has not been processed yet).
     */
    @Transactional(readOnly = true)
    public List<Category> list(UUID ownerId, CategoryType type) {
        if (!categories.isSeeded(ownerId)) {
            try {
                seeder.seed(ownerId);
            } catch (DataIntegrityViolationException e) {
                // seeded concurrently by the registration listener – the defaults exist now
            }
        }
        return categories.findByOwner(ownerId, type);
    }

    public Category create(UUID ownerId, String name, CategoryType type, UUID parentId, String icon) {
        Category parent = parentId == null ? null : load(parentId, ownerId);
        return categories.save(Category.create(ownerId, name, type, parent, icon));
    }

    public Category update(UUID id, UUID ownerId, String name, String icon) {
        Category category = load(id, ownerId);
        category.update(name, icon);
        return categories.save(category);
    }

    /** A category that is used by transactions, recurring rules or sub-categories cannot be deleted. */
    public void delete(UUID id, UUID ownerId) {
        Category category = load(id, ownerId);
        if (categories.hasChildren(id)) {
            throw new DomainException("CATEGORY_HAS_CHILDREN", "Delete the sub-categories first");
        }
        if (transactions.existsByCategory(id) || recurring.existsByCategory(id)) {
            throw new DomainException("CATEGORY_IN_USE", "Category is used by transactions");
        }
        categories.delete(category);
    }

    private Category load(UUID id, UUID ownerId) {
        return categories.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Category", id));
    }
}
