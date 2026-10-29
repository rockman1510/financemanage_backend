package com.financemanage.ledger.domain.model;

import com.financemanage.shared.domain.DomainException;
import java.util.Objects;
import java.util.UUID;

/**
 * Income or expense category (nhóm thu/chi). Categories form at most two levels: a top-level category and its
 * sub-categories, which always have the parent's type. Default categories carry a stable {@code code} so clients can
 * translate them.
 */
public class Category {

    private static final int MAX_NAME_LENGTH = 60;

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private final CategoryType type;
    private final UUID parentId;
    private String icon;
    private final String code;

    public Category(UUID id, UUID ownerId, String name, CategoryType type, UUID parentId, String icon, String code) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = name;
        this.type = Objects.requireNonNull(type);
        this.parentId = parentId;
        this.icon = icon;
        this.code = code;
    }

    /** A category created by the user, optionally under {@code parent} (which must be a top-level category). */
    public static Category create(UUID ownerId, String name, CategoryType type, Category parent, String icon) {
        if (type == null) {
            throw new DomainException("CATEGORY_TYPE_REQUIRED", "Category type is required");
        }
        if (parent != null) {
            if (parent.parentId != null) {
                throw new DomainException("CATEGORY_TOO_DEEP", "Sub-categories cannot have sub-categories");
            }
            if (parent.type != type) {
                throw new DomainException("CATEGORY_TYPE_MISMATCH", "A sub-category must have its parent's type");
            }
        }
        return new Category(UUID.randomUUID(), ownerId, validName(name), type, parent == null ? null : parent.id,
                Texts.optional(icon), null);
    }

    static Category seeded(UUID ownerId, String code, String name, CategoryType type, UUID parentId, String icon) {
        return new Category(UUID.randomUUID(), ownerId, name, type, parentId, icon, code);
    }

    /** Partial update: {@code null} fields stay unchanged. */
    public void update(String newName, String newIcon) {
        if (newName != null) {
            name = validName(newName);
        }
        if (newIcon != null) {
            icon = Texts.optional(newIcon);
        }
    }

    private static String validName(String value) {
        return Texts.required(value, MAX_NAME_LENGTH, "CATEGORY_NAME_REQUIRED", "Category name");
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public CategoryType getType() { return type; }
    public UUID getParentId() { return parentId; }
    public String getIcon() { return icon; }
    public String getCode() { return code; }
}
