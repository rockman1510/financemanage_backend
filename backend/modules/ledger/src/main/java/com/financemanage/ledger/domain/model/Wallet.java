package com.financemanage.ledger.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Wallet aggregate (ví): a pot of money whose balance the owner keeps by recording transactions – no bank
 * connection. {@code balance} = initial balance + the signed amounts of all its transactions; it is updated in the
 * same database transaction as the transaction itself. Pure Java so business rules are easy to test.
 */
public class Wallet {

    private static final int MAX_NAME_LENGTH = 60;

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private final WalletType type;
    private Money initialBalance;
    private Money balance;
    private boolean excludedFromTotal;
    private boolean archived;
    private String icon;
    private Money creditLimit;
    private Integer statementDay;
    private Integer paymentDueDay;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Wallet(UUID id, UUID ownerId, String name, WalletType type, Money initialBalance, Money balance,
                  boolean excludedFromTotal, boolean archived, String icon, Money creditLimit, Integer statementDay,
                  Integer paymentDueDay, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = name;
        this.type = Objects.requireNonNull(type);
        this.initialBalance = Objects.requireNonNull(initialBalance);
        this.balance = Objects.requireNonNull(balance);
        this.excludedFromTotal = excludedFromTotal;
        this.archived = archived;
        this.icon = icon;
        this.creditLimit = creditLimit;
        this.statementDay = statementDay;
        this.paymentDueDay = paymentDueDay;
        this.version = version;
    }

    public static Wallet open(NewWallet spec) {
        if (spec.type() == null) {
            throw new DomainException("WALLET_TYPE_REQUIRED", "Wallet type is required");
        }
        Money initial = Texts.precise(Objects.requireNonNull(spec.initialBalance(), "initialBalance"));
        Wallet wallet = new Wallet(UUID.randomUUID(), spec.ownerId(),
                Texts.required(spec.name(), MAX_NAME_LENGTH, "WALLET_NAME_REQUIRED", "Wallet name"), spec.type(),
                initial, initial, spec.excludedFromTotal(), false, Texts.optional(spec.icon()), null, null, null, null);
        wallet.changeCreditCardTerms(spec.creditLimit(), spec.statementDay(), spec.paymentDueDay());
        return wallet;
    }

    /** Applies a partial update: {@code null} fields stay unchanged. */
    public void update(WalletChanges changes) {
        if (changes.name() != null) {
            name = Texts.required(changes.name(), MAX_NAME_LENGTH, "WALLET_NAME_REQUIRED", "Wallet name");
        }
        if (changes.icon() != null) {
            icon = Texts.optional(changes.icon());
        }
        if (changes.excludedFromTotal() != null) {
            excludedFromTotal = changes.excludedFromTotal();
        }
        if (changes.archived() != null) {
            archived = changes.archived();
        }
        if (changes.initialBalance() != null) {
            Money newInitial = Texts.precise(new Money(changes.initialBalance(), currency()));
            balance = balance.plus(newInitial.minus(initialBalance));
            initialBalance = newInitial;
        }
        changeCreditCardTerms(
                changes.creditLimit() != null ? new Money(changes.creditLimit(), currency()) : creditLimit,
                changes.statementDay() != null ? changes.statementDay() : statementDay,
                changes.paymentDueDay() != null ? changes.paymentDueDay() : paymentDueDay);
    }

    /** Adds the signed amount of a transaction (negative to take money out). */
    public void apply(Money signedAmount) {
        balance = balance.plus(signedAmount);
    }

    /** Archived wallets keep their history but accept no new transactions. */
    public void requireUsable() {
        if (archived) {
            throw new DomainException("WALLET_ARCHIVED", "Wallet " + name + " is archived");
        }
    }

    public void requireCurrency(Money amount) {
        if (!amount.currency().equals(currency())) {
            throw new DomainException("CURRENCY_MISMATCH",
                    "Wallet " + name + " holds " + currencyCode() + ", not " + amount.currencyCode());
        }
    }

    /** Remaining credit of a credit card (limit + balance, the balance being negative when in debt). */
    public Money availableCredit() {
        return creditLimit == null ? null : creditLimit.plus(balance);
    }

    private void changeCreditCardTerms(Money limit, Integer statement, Integer paymentDue) {
        if (type != WalletType.CREDIT_CARD) {
            return; // only credit cards have a limit and billing days
        }
        if (limit != null && (limit.isNegative() || !limit.hasMinorUnitPrecision())) {
            throw new DomainException("INVALID_CREDIT_LIMIT", "Credit limit must be a non-negative amount");
        }
        creditLimit = limit;
        statementDay = day(statement);
        paymentDueDay = day(paymentDue);
    }

    private static Integer day(Integer value) {
        if (value != null && (value < 1 || value > 31)) {
            throw new DomainException("INVALID_DAY_OF_MONTH", "Day of month must be between 1 and 31");
        }
        return value;
    }

    public Currency currency() {
        return balance.currency();
    }

    public String currencyCode() {
        return balance.currencyCode();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public WalletType getType() { return type; }
    public Money getInitialBalance() { return initialBalance; }
    public Money getBalance() { return balance; }
    public boolean isExcludedFromTotal() { return excludedFromTotal; }
    public boolean isArchived() { return archived; }
    public String getIcon() { return icon; }
    public Money getCreditLimit() { return creditLimit; }
    public Integer getStatementDay() { return statementDay; }
    public Integer getPaymentDueDay() { return paymentDueDay; }
    public Long getVersion() { return version; }

    /** Input for opening a wallet. */
    public record NewWallet(UUID ownerId, String name, WalletType type, Money initialBalance, String icon,
                            boolean excludedFromTotal, Money creditLimit, Integer statementDay,
                            Integer paymentDueDay) {
    }

    /** Partial update of a wallet; {@code null} means unchanged. The currency and type never change. */
    public record WalletChanges(String name, String icon, Boolean excludedFromTotal, Boolean archived,
                                BigDecimal initialBalance, BigDecimal creditLimit, Integer statementDay,
                                Integer paymentDueDay) {
    }
}
