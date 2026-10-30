package com.financemanage.ledger.application;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.ledger.domain.AttachmentRepository;
import com.financemanage.ledger.domain.CategoryRepository;
import com.financemanage.ledger.domain.RecurringTransactionRepository;
import com.financemanage.ledger.domain.TransactionRepository;
import com.financemanage.ledger.domain.WalletRepository;
import com.financemanage.ledger.domain.model.Attachment;
import com.financemanage.ledger.domain.model.Category;
import com.financemanage.ledger.domain.model.CategoryType;
import com.financemanage.ledger.domain.model.PageResult;
import com.financemanage.ledger.domain.model.RecurringTransaction;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.TransactionFilter;
import com.financemanage.ledger.domain.model.TransactionTotals;
import com.financemanage.ledger.domain.model.TransactionType;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.util.unit.DataSize;

/**
 * Test doubles for the ledger's outbound ports, wired into real services – keeps service tests fast and free of a
 * database.
 */
final class InMemoryLedger {

    static final LocalDate TODAY = LocalDate.of(2025, 4, 1);

    final Wallets wallets = new Wallets();
    final Categories categories = new Categories();
    final Transactions transactions = new Transactions();
    final Rules rules = new Rules();
    final Attachments attachments = new Attachments();
    final Storage storage = new Storage();
    final Rates rates = new Rates();
    final List<Object> events = new ArrayList<>();
    final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    final AttachmentService attachmentService =
            new AttachmentService(attachments, transactions, storage, clock, DataSize.ofKilobytes(10));
    final TransactionService transactionService = new TransactionService(transactions, wallets, categories,
            attachmentService, rates, events::add, clock);
    final WalletService walletService = new WalletService(wallets, transactions, rules, rates, events::add, clock);
    final RecurringTransactionService recurringService =
            new RecurringTransactionService(rules, wallets, categories, transactionService, clock);
    final CategoryService categoryService =
            new CategoryService(categories, new CategorySeeder(categories), transactions, rules);

    Wallet add(Wallet wallet) {
        wallets.save(wallet);
        return wallet;
    }

    Category add(Category category) {
        return categories.save(category);
    }

    Money balance(Wallet wallet) {
        return wallets.store.get(wallet.getId()).getBalance();
    }

    static class Wallets implements WalletRepository {
        final Map<UUID, Wallet> store = new LinkedHashMap<>();

        @Override
        public Wallet save(Wallet wallet) {
            store.put(wallet.getId(), wallet);
            return wallet;
        }

        @Override
        public Optional<Wallet> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(w -> w.getOwnerId().equals(ownerId));
        }

        @Override
        public List<Wallet> findByOwner(UUID ownerId, boolean includeArchived) {
            return store.values().stream().filter(w -> w.getOwnerId().equals(ownerId))
                    .filter(w -> includeArchived || !w.isArchived()).toList();
        }

        @Override
        public void delete(Wallet wallet) {
            store.remove(wallet.getId());
        }
    }

    static class Categories implements CategoryRepository {
        final Map<UUID, Category> store = new LinkedHashMap<>();
        final Set<UUID> seeded = new HashSet<>();

        @Override
        public Category save(Category category) {
            store.put(category.getId(), category);
            return category;
        }

        @Override
        public void saveAll(List<Category> categories) {
            categories.forEach(this::save);
        }

        @Override
        public Optional<Category> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(c -> c.getOwnerId().equals(ownerId));
        }

        @Override
        public List<Category> findByOwner(UUID ownerId, CategoryType type) {
            return store.values().stream().filter(c -> c.getOwnerId().equals(ownerId))
                    .filter(c -> type == null || c.getType() == type).toList();
        }

        @Override
        public boolean hasChildren(UUID categoryId) {
            return store.values().stream().anyMatch(c -> categoryId.equals(c.getParentId()));
        }

        @Override
        public void delete(Category category) {
            store.remove(category.getId());
        }

        @Override
        public boolean isSeeded(UUID ownerId) {
            return seeded.contains(ownerId);
        }

        @Override
        public void markSeeded(UUID ownerId) {
            seeded.add(ownerId);
        }
    }

    static class Transactions implements TransactionRepository {
        final Map<UUID, Transaction> store = new LinkedHashMap<>();

        @Override
        public Transaction save(Transaction transaction) {
            store.put(transaction.getId(), transaction);
            return transaction;
        }

        @Override
        public Optional<Transaction> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(t -> t.getOwnerId().equals(ownerId));
        }

        @Override
        public Optional<Transaction> findByIdempotencyKey(UUID ownerId, String key) {
            return store.values().stream()
                    .filter(t -> t.getOwnerId().equals(ownerId) && key.equals(t.getIdempotencyKey())).findFirst();
        }

        @Override
        public List<Transaction> findByTransfer(UUID ownerId, UUID transferId) {
            return store.values().stream()
                    .filter(t -> t.getOwnerId().equals(ownerId) && transferId.equals(t.getTransferId()))
                    .sorted(Comparator.comparing((Transaction t) -> t.getType().name()).reversed()).toList();
        }

        @Override
        public PageResult<Transaction> search(TransactionFilter filter, int page, int size) {
            List<Transaction> all = matching(filter)
                    .sorted(Comparator.comparing(Transaction::getOccurredOn).reversed()).toList();
            return new PageResult<>(all.stream().skip((long) page * size).limit(size).toList(), all.size());
        }

        @Override
        public List<TransactionTotals> totals(TransactionFilter filter) {
            Map<String, Money[]> sums = new TreeMap<>();
            matching(filter).filter(t -> !t.isExcludeFromReport() && t.getType().isCategorized()).forEach(t -> {
                Money[] pair = sums.computeIfAbsent(t.getAmount().currencyCode(),
                        c -> new Money[] {Money.zero(t.getAmount().currency()), Money.zero(t.getAmount().currency())});
                int index = t.getType() == TransactionType.INCOME ? 0 : 1;
                pair[index] = pair[index].plus(t.getAmount());
            });
            return sums.values().stream().map(p -> new TransactionTotals(p[0], p[1])).toList();
        }

        @Override
        public boolean existsByWallet(UUID walletId) {
            return store.values().stream().anyMatch(t -> t.getWalletId().equals(walletId));
        }

        @Override
        public boolean existsByCategory(UUID categoryId) {
            return store.values().stream().anyMatch(t -> categoryId.equals(t.getCategoryId()));
        }

        @Override
        public void delete(Transaction transaction) {
            store.remove(transaction.getId());
        }

        private Stream<Transaction> matching(TransactionFilter f) {
            return store.values().stream()
                    .filter(t -> t.getOwnerId().equals(f.ownerId()))
                    .filter(t -> f.walletId() == null || f.walletId().equals(t.getWalletId()))
                    .filter(t -> f.categoryIds().isEmpty()
                            || (t.getCategoryId() != null && f.categoryIds().contains(t.getCategoryId())))
                    .filter(t -> f.types().isEmpty() || f.types().contains(t.getType()))
                    .filter(t -> f.from() == null || !t.getOccurredOn().isBefore(f.from()))
                    .filter(t -> f.to() == null || !t.getOccurredOn().isAfter(f.to()))
                    .filter(t -> f.minAmount() == null || t.getAmount().amount().compareTo(f.minAmount()) >= 0)
                    .filter(t -> f.maxAmount() == null || t.getAmount().amount().compareTo(f.maxAmount()) <= 0)
                    .filter(t -> f.text() == null || (t.getNote() != null && t.getNote().contains(f.text())));
        }
    }

    static class Rules implements RecurringTransactionRepository {
        final Map<UUID, RecurringTransaction> store = new LinkedHashMap<>();

        @Override
        public RecurringTransaction save(RecurringTransaction rule) {
            store.put(rule.getId(), rule);
            return rule;
        }

        @Override
        public Optional<RecurringTransaction> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<RecurringTransaction> findByIdAndOwner(UUID id, UUID ownerId) {
            return findById(id).filter(r -> r.getOwnerId().equals(ownerId));
        }

        @Override
        public List<RecurringTransaction> findByOwner(UUID ownerId) {
            return store.values().stream().filter(r -> r.getOwnerId().equals(ownerId)).toList();
        }

        @Override
        public List<UUID> findDueIds(LocalDate date) {
            return store.values().stream().filter(r -> r.isDue(date)).map(RecurringTransaction::getId).toList();
        }

        @Override
        public boolean existsByCategory(UUID categoryId) {
            return store.values().stream().anyMatch(r -> r.getCategoryId().equals(categoryId));
        }

        @Override
        public void deleteByWallet(UUID walletId) {
            store.values().removeIf(r -> r.getWalletId().equals(walletId));
        }

        @Override
        public void delete(RecurringTransaction rule) {
            store.remove(rule.getId());
        }
    }

    static class Attachments implements AttachmentRepository {
        final Map<UUID, Attachment> store = new LinkedHashMap<>();

        @Override
        public Attachment save(Attachment attachment) {
            store.put(attachment.id(), attachment);
            return attachment;
        }

        @Override
        public Optional<Attachment> findByIdAndOwner(UUID id, UUID ownerId) {
            return Optional.ofNullable(store.get(id)).filter(a -> a.ownerId().equals(ownerId));
        }

        @Override
        public List<Attachment> findByTransaction(UUID transactionId) {
            return store.values().stream().filter(a -> a.transactionId().equals(transactionId)).toList();
        }

        @Override
        public void delete(Attachment attachment) {
            store.remove(attachment.id());
        }
    }

    static class Storage implements AttachmentStorage {
        final Map<String, byte[]> files = new HashMap<>();

        @Override
        public void store(String key, InputStream content) {
            try {
                files.put(key, content.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream open(String key) {
            return new ByteArrayInputStream(files.get(key));
        }

        @Override
        public void delete(String key) {
            files.remove(key);
        }
    }

    /** Fixed exchange rates: "USD->VND" = 25000 and its inverse. */
    static class Rates implements CurrencyConversionApi {
        final Map<String, BigDecimal> rates = new HashMap<>(Map.of("USD->VND", new BigDecimal("25000"),
                "VND->USD", new BigDecimal("0.00004")));

        @Override
        public Optional<BigDecimal> rate(UUID ownerId, String from, String to, LocalDate asOf) {
            return from.equals(to) ? Optional.of(BigDecimal.ONE) : Optional.ofNullable(rates.get(from + "->" + to));
        }

        @Override
        public Money convert(UUID ownerId, Money amount, String targetCurrency, LocalDate asOf) {
            BigDecimal rate = rate(ownerId, amount.currencyCode(), targetCurrency, asOf)
                    .orElseThrow(() -> new DomainException("EXCHANGE_RATE_MISSING", "No rate"));
            return Money.of(amount.amount().multiply(rate), targetCurrency).rounded();
        }
    }
}
