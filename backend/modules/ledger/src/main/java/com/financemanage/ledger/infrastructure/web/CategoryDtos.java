package com.financemanage.ledger.infrastructure.web;

import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request/response contracts of the category REST API (v1). */
final class CategoryDtos {

    private CategoryDtos() {
    }

    record CreateCategoryRequest(@NotBlank @Size(max = 60) String name, @NotNull CategoryType type, UUID parentId,
                                 @Size(max = 40) String icon) {
    }

    record UpdateCategoryRequest(@Size(max = 60) String name, @Size(max = 40) String icon) {
    }

    /** {@code code} is set for default categories so clients can show a translated name. */
    record CategoryResponse(UUID id, String name, CategoryType type, UUID parentId, String icon, String code) {

        static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getParentId(), c.getIcon(),
                    c.getCode());
        }
    }
}
