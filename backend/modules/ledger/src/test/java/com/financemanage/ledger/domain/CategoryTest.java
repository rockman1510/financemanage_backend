package com.financemanage.ledger.domain;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.expense;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import com.financemanage.ledger.domain.model.DefaultCategories;
import com.financemanage.shared.domain.DomainException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    void subCategoriesHaveTheParentsTypeAndOnlyTwoLevels() {
        Category food = expense("Ăn uống");
        Category coffee = Category.create(OWNER, "Cà phê", CategoryType.EXPENSE, food, null);

        assertThat(coffee.getParentId()).isEqualTo(food.getId());
        assertThatThrownBy(() -> Category.create(OWNER, "Trà sữa", CategoryType.EXPENSE, coffee, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CATEGORY_TOO_DEEP");
        assertThatThrownBy(() -> Category.create(OWNER, "Lương", CategoryType.INCOME, food, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CATEGORY_TYPE_MISMATCH");
    }

    @Test
    void defaultsHaveUniqueCodesAndValidParents() {
        UUID owner = UUID.randomUUID();
        List<Category> defaults = DefaultCategories.forOwner(owner);
        Map<UUID, Category> byId = defaults.stream().collect(Collectors.toMap(Category::getId, Function.identity()));

        assertThat(defaults).allMatch(c -> c.getOwnerId().equals(owner) && c.getCode() != null);
        assertThat(defaults.stream().map(Category::getCode).distinct()).hasSameSizeAs(defaults);
        assertThat(defaults).filteredOn(c -> c.getParentId() != null).allSatisfy(child -> {
            Category parent = byId.get(child.getParentId());
            assertThat(parent.getType()).isEqualTo(child.getType());
            assertThat(defaults.indexOf(parent)).isLessThan(defaults.indexOf(child));
        });
        assertThat(defaults).anyMatch(c -> c.getType() == CategoryType.INCOME);
    }
}
