package com.financemanage.ledger.infrastructure.persistence;

import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;
    private final CategorySeedJpaRepository seeds;
    private final EntityManager entityManager;

    CategoryRepositoryAdapter(CategoryJpaRepository jpa, CategorySeedJpaRepository seeds,
                              EntityManager entityManager) {
        this.jpa = jpa;
        this.seeds = seeds;
        this.entityManager = entityManager;
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity = jpa.findById(category.getId()).orElseGet(CategoryJpaEntity::new);
        copy(category, entity);
        return toDomain(jpa.saveAndFlush(entity));
    }

    @Override
    public void saveAll(List<Category> categories) {
        for (Category category : categories) {
            CategoryJpaEntity entity = new CategoryJpaEntity();
            copy(category, entity);
            entityManager.persist(entity); // new rows only: plain INSERTs, parents before children
        }
        entityManager.flush();
    }

    @Override
    public Optional<Category> findByIdAndOwner(UUID id, UUID ownerId) {
        return jpa.findByIdAndOwnerId(id, ownerId).map(this::toDomain);
    }

    @Override
    public List<Category> findByOwner(UUID ownerId, CategoryType type) {
        List<Category> all = jpa.findByOwnerIdOrderByCreatedAtAscNameAsc(ownerId).stream()
                .filter(c -> type == null || c.type == type)
                .map(this::toDomain).toList();
        // parents first, each followed by its children, so clients can render the tree in one pass
        List<Category> ordered = new ArrayList<>();
        for (Category parent : all) {
            if (parent.getParentId() == null) {
                ordered.add(parent);
                all.stream().filter(c -> parent.getId().equals(c.getParentId())).forEach(ordered::add);
            }
        }
        return ordered;
    }

    @Override
    public boolean hasChildren(UUID categoryId) {
        return jpa.existsByParentId(categoryId);
    }

    @Override
    public void delete(Category category) {
        jpa.deleteById(category.getId());
    }

    @Override
    public boolean isSeeded(UUID ownerId) {
        return seeds.existsById(ownerId);
    }

    @Override
    public void markSeeded(UUID ownerId) {
        CategorySeedJpaEntity seed = new CategorySeedJpaEntity();
        seed.ownerId = ownerId;
        entityManager.persist(seed); // INSERT – fails on the primary key when seeded concurrently
        entityManager.flush();
    }

    private static void copy(Category category, CategoryJpaEntity entity) {
        entity.id = category.getId();
        entity.ownerId = category.getOwnerId();
        entity.name = category.getName();
        entity.type = category.getType();
        entity.parentId = category.getParentId();
        entity.icon = category.getIcon();
        entity.code = category.getCode();
    }

    private Category toDomain(CategoryJpaEntity e) {
        return new Category(e.id, e.ownerId, e.name, e.type, e.parentId, e.icon, e.code);
    }
}
