package com.financemanage.investment.domain.model;

import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An investment holding (tài sản đầu tư), e.g. 100 FPT shares or 2 lượng vàng SJC. Kept at average cost: a buy adds
 * its price and fee to the cost, a sell takes out the average cost of what is sold and books the rest as a realized
 * gain. The value comes from the last known price (entered by hand or by a price provider). Pure Java.
 */
public class Asset {

    private static final int MAX_NAME_LENGTH = 60;
    private static final int QUANTITY_SCALE = 8;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final UUID id;
    private final UUID ownerId;
    private String name;
    private String symbol;
    private final AssetType type;
    private final Currency currency;
    private String note;
    private BigDecimal quantity;
    /** What the held quantity cost, fees included. */
    private Money costBasis;
    /** Gains of sells and incomes so far. */
    private Money realized;
    private BigDecimal lastPrice;
    private LocalDate priceDate;
    private final List<Trade> trades;
    private final Long version;

    @SuppressWarnings("java:S107") // reconstitution constructor used by the persistence adapter
    public Asset(UUID id, UUID ownerId, String name, String symbol, AssetType type, Currency currency, String note,
                 BigDecimal quantity, Money costBasis, Money realized, BigDecimal lastPrice, LocalDate priceDate,
                 List<Trade> trades, Long version) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.name = name;
        this.symbol = symbol;
        this.type = Objects.requireNonNull(type);
        this.currency = Objects.requireNonNull(currency);
        this.note = note;
        this.quantity = Objects.requireNonNull(quantity);
        this.costBasis = Objects.requireNonNull(costBasis);
        this.realized = Objects.requireNonNull(realized);
        this.lastPrice = lastPrice;
        this.priceDate = priceDate;
        this.trades = new ArrayList<>(trades == null ? List.of() : trades);
        this.version = version;
    }

    public static Asset create(UUID ownerId, String name, String symbol, AssetType type, Currency currency,
                               String note) {
        if (type == null) {
            throw new DomainException("ASSET_TYPE_REQUIRED", "Asset type is required");
        }
        return new Asset(UUID.randomUUID(), ownerId, validName(name), symbolOf(symbol), type, currency, text(note),
                BigDecimal.ZERO, Money.zero(currency), Money.zero(currency), null, null, List.of(), null);
    }

    /** Partial update: {@code null} values stay unchanged. */
    public void update(String newName, String newSymbol, String newNote) {
        if (newName != null) {
            name = validName(newName);
        }
        if (newSymbol != null) {
            symbol = symbolOf(newSymbol);
        }
        if (newNote != null) {
            note = text(newNote);
        }
    }

    /** Buys {@code qty} at {@code price} per unit; the cash paid is qty × price + fee. */
    public Trade buy(BigDecimal qty, BigDecimal price, BigDecimal fee, UUID walletId, LocalDate date, Instant now) {
        BigDecimal units = validQuantity(qty);
        BigDecimal unitPrice = validPrice(price);
        Money feeMoney = feeOf(fee);
        Money cash = money(units.multiply(unitPrice)).plus(feeMoney).rounded(); // what the wallet pays
        quantity = quantity.add(units);
        costBasis = costBasis.plus(cash);
        observePrice(unitPrice, date);
        return add(new Trade(UUID.randomUUID(), TradeType.BUY, units, unitPrice, feeMoney, cash, null, date,
                walletId, cashStatus(walletId), null, now));
    }

    /** Sells {@code qty}: the average cost of the units leaves the cost basis, the rest of the proceeds is a gain. */
    public Trade sell(BigDecimal qty, BigDecimal price, BigDecimal fee, UUID walletId, LocalDate date, Instant now) {
        BigDecimal units = validQuantity(qty);
        if (units.compareTo(quantity) > 0) {
            throw new DomainException("INSUFFICIENT_QUANTITY", "Only " + quantity.stripTrailingZeros().toPlainString()
                    + " units are held");
        }
        BigDecimal unitPrice = validPrice(price);
        Money feeMoney = feeOf(fee);
        Money proceeds = money(units.multiply(unitPrice)).minus(feeMoney).rounded();
        if (proceeds.isNegative()) {
            throw new DomainException("FEE_TOO_HIGH", "The fee is larger than what the sale brings in");
        }
        Money soldCost = units.compareTo(quantity) == 0 ? costBasis
                : money(costBasis.amount().multiply(units).divide(quantity, Money.CALC_SCALE, Money.ROUNDING));
        Money gain = proceeds.minus(soldCost);
        quantity = quantity.subtract(units);
        costBasis = costBasis.minus(soldCost);
        realized = realized.plus(gain);
        observePrice(unitPrice, date);
        return add(new Trade(UUID.randomUUID(), TradeType.SELL, units, unitPrice, feeMoney, proceeds, gain, date,
                walletId, cashStatus(walletId), null, now));
    }

    /** A dividend, coupon or rent received; all of it is a realized gain. */
    public Trade income(BigDecimal amount, UUID walletId, LocalDate date, Instant now) {
        if (amount == null || amount.signum() <= 0) {
            throw new DomainException("INVALID_AMOUNT", "Amount must be positive");
        }
        Money cash = money(amount).rounded();
        realized = realized.plus(cash);
        return add(new Trade(UUID.randomUUID(), TradeType.INCOME, null, null, Money.zero(currency), cash, cash, date,
                walletId, cashStatus(walletId), null, now));
    }

    /** Sets the market price per unit; an older price than the one known is ignored. */
    public void updatePrice(BigDecimal price, LocalDate date) {
        BigDecimal unitPrice = validPrice(price);
        if (priceDate != null && date.isBefore(priceDate)) {
            throw new DomainException("STALE_PRICE", "A price of " + priceDate + " is already known");
        }
        lastPrice = unitPrice;
        priceDate = date;
    }

    public Valuation valuation() {
        boolean holding = quantity.signum() > 0;
        BigDecimal averageCost = holding
                ? costBasis.amount().divide(quantity, Money.CALC_SCALE, Money.ROUNDING) : null;
        boolean priced = lastPrice != null;
        Money market = priced ? money(quantity.multiply(lastPrice)) : costBasis;
        Money unrealized = market.minus(costBasis);
        BigDecimal percent = costBasis.isPositive()
                ? unrealized.amount().multiply(HUNDRED).divide(costBasis.amount(), 2, RoundingMode.HALF_UP) : null;
        return new Valuation(averageCost, market, unrealized, percent, unrealized.plus(realized), priced);
    }

    public boolean isHolding() {
        return quantity.signum() > 0;
    }

    private void observePrice(BigDecimal price, LocalDate date) {
        if (priceDate == null || !date.isBefore(priceDate)) {
            lastPrice = price;
            priceDate = date;
        }
    }

    private Trade add(Trade trade) {
        trades.add(trade);
        return trade;
    }

    private Money money(BigDecimal amount) {
        return new Money(amount, currency);
    }

    private Money feeOf(BigDecimal fee) {
        if (fee != null && fee.signum() < 0) {
            throw new DomainException("INVALID_FEE", "Fee cannot be negative");
        }
        return money(fee == null ? BigDecimal.ZERO : fee);
    }

    private static CashStatus cashStatus(UUID walletId) {
        return walletId == null ? CashStatus.NONE : CashStatus.PENDING;
    }

    private static BigDecimal validQuantity(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            throw new DomainException("INVALID_QUANTITY", "Quantity must be positive");
        }
        if (qty.stripTrailingZeros().scale() > QUANTITY_SCALE) {
            throw new DomainException("INVALID_QUANTITY", "Quantity has at most " + QUANTITY_SCALE + " decimals");
        }
        return qty;
    }

    private static BigDecimal validPrice(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new DomainException("INVALID_PRICE", "Price must be zero or positive");
        }
        return price;
    }

    private static String validName(String value) {
        if (value == null || value.isBlank() || value.strip().length() > MAX_NAME_LENGTH) {
            throw new DomainException("INVALID_NAME", "Name must have 1-" + MAX_NAME_LENGTH + " characters");
        }
        return value.strip();
    }

    private static String symbolOf(String value) {
        return value == null || value.isBlank() ? null : value.strip().toUpperCase();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public AssetType getType() { return type; }
    public Currency getCurrency() { return currency; }
    public String getNote() { return note; }
    public BigDecimal getQuantity() { return quantity; }
    public Money getCostBasis() { return costBasis; }
    public Money getRealized() { return realized; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public LocalDate getPriceDate() { return priceDate; }
    public List<Trade> getTrades() { return Collections.unmodifiableList(trades); }
    public Long getVersion() { return version; }
}
