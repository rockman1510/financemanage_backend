package com.financemanage.ledger.application;

import static com.financemanage.ledger.LedgerFixtures.OWNER;
import static com.financemanage.ledger.LedgerFixtures.wallet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.DomainException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CategoryAndAttachmentServiceTest {

    private final InMemoryLedger ledger = new InMemoryLedger();
    private final CategoryService categories = ledger.categoryService;
    private final AttachmentService attachments = ledger.attachmentService;

    @Test
    void listingCreatesTheDefaultCategoriesOnce() {
        int count = categories.list(OWNER, null).size();

        assertThat(count).isGreaterThan(20);
        assertThat(categories.list(OWNER, null)).hasSize(count);
        assertThat(categories.list(OWNER, CategoryType.INCOME)).allMatch(c -> c.getType() == CategoryType.INCOME);
    }

    @Test
    void usedCategoriesAndParentsCannotBeDeleted() {
        Category food = categories.create(OWNER, "Ăn vặt", CategoryType.EXPENSE, null, null);
        Category snack = categories.create(OWNER, "Bánh", CategoryType.EXPENSE, food.getId(), null);
        record(snack);

        assertThatThrownBy(() -> categories.delete(food.getId(), OWNER))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CATEGORY_HAS_CHILDREN");
        assertThatThrownBy(() -> categories.delete(snack.getId(), OWNER))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("CATEGORY_IN_USE");
    }

    @Test
    void attachmentsAreValidated() {
        Transaction t = record(categories.create(OWNER, "Ăn vặt", CategoryType.EXPENSE, null, null));

        assertThatThrownBy(() -> upload(t, "virus.exe", "application/octet-stream", 3))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("ATTACHMENT_TYPE_NOT_ALLOWED");
        assertThatThrownBy(() -> upload(t, "big.jpg", "image/jpeg", 20 * 1024))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("ATTACHMENT_TOO_LARGE");
        for (int i = 0; i < AttachmentService.MAX_PER_TRANSACTION; i++) {
            upload(t, "bill.jpg", "image/jpeg", 3);
        }
        assertThatThrownBy(() -> upload(t, "bill.jpg", "image/jpeg", 3))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("TOO_MANY_ATTACHMENTS");
        assertThat(ledger.storage.files).hasSize(AttachmentService.MAX_PER_TRANSACTION);
    }

    @Test
    void fileNamesAreCleaned() {
        assertThat(List.of(AttachmentService.cleanFileName("../../etc/passwd"),
                AttachmentService.cleanFileName("C:\\bills\\hóa đơn.jpg"),
                AttachmentService.cleanFileName("a\n\"b\".png"), AttachmentService.cleanFileName(null)))
                .containsExactly("passwd", "hóa đơn.jpg", "ab.png", "attachment");
    }

    private Transaction record(Category category) {
        Wallet cash = ledger.add(wallet("Tiền mặt", 0));
        return ledger.transactionService.record(OWNER, new TransactionInput(cash.getId(), category.getId(),
                BigDecimal.TEN, null, null, null, false), null);
    }

    private void upload(Transaction t, String name, String type, int size) {
        attachments.upload(OWNER, t.getId(), name, type, size, new ByteArrayInputStream(new byte[size]));
    }
}
