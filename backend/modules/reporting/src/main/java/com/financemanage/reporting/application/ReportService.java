package com.financemanage.reporting.application;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.reporting.application.NetWorthCalculator.Item;
import com.financemanage.reporting.application.NetWorthCalculator.Kind;
import com.financemanage.reporting.domain.ReportEntry;
import com.financemanage.reporting.domain.ReportEntryStore;
import com.financemanage.reporting.domain.ReportEntryStore.DailyTotal;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Income and expense reports over the read model, converted to one currency with the owner's exchange rates (at the
 * end of the period, or today if the period is not over). Transfers and adjustments never count; movements to and
 * from savings, investments or deposits ({@code EXTERNAL_*}) only show in the cash flow.
 */
@Service
public class ReportService {

    static final String INCOME = "INCOME";
    static final String EXPENSE = "EXPENSE";
    static final String EXTERNAL_IN = "EXTERNAL_IN";
    static final String EXTERNAL_OUT = "EXTERNAL_OUT";
    private static final int MAX_DAYS = 3660;
    private static final int MAX_DAILY_POINTS = 366;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ReportProjection projection;
    private final ReportEntryStore store;
    private final LedgerQueryApi ledger;
    private final CurrencyConversionApi conversion;
    private final NetWorthCalculator netWorth;
    private final Clock clock;

    public ReportService(ReportProjection projection, ReportEntryStore store, LedgerQueryApi ledger,
                         CurrencyConversionApi conversion, NetWorthCalculator netWorth, Clock clock) {
        this.projection = projection;
        this.store = store;
        this.ledger = ledger;
        this.conversion = conversion;
        this.netWorth = netWorth;
        this.clock = clock;
    }

    public enum Granularity { DAY, MONTH }

    /** A period: both dates included. */
    public record Period(LocalDate from, LocalDate to) {
    }

    public record Totals(Money income, Money expense, Money net) {
    }

    /**
     * {@code previous} is the period of the same length just before (whole months when the period is whole months);
     * changes are in percent of the previous value (null when it was zero); {@code savingsRate} = net / income.
     */
    public record Summary(Period period, String currency, Totals totals, BigDecimal savingsRate, Period previousPeriod,
                          Totals previous, BigDecimal incomeChange, BigDecimal expenseChange) {
    }

    public record CategoryTotal(UUID categoryId, String name, Money amount, BigDecimal percent,
                                List<CategoryTotal> children) {
    }

    public record CategoryReport(Period period, String type, String currency, Money total,
                                 List<CategoryTotal> categories) {
    }

    /** {@code net} = income − expense; {@code cashFlow} also counts money moved to/from savings, investments… */
    public record CashFlowPoint(String period, Money income, Money expense, Money net, Money externalIn,
                                Money externalOut, Money cashFlow) {
    }

    public record CashFlowReport(Period period, String currency, Granularity granularity,
                                 List<CashFlowPoint> points) {
    }

    public record NetWorthItem(Kind kind, UUID id, String name, Money original, Money converted) {
    }

    public record NetWorthGroup(Kind kind, Money total, List<NetWorthItem> items) {
    }

    /** {@code liabilities} are the debts (BORROWED); net worth = assets − liabilities. */
    public record NetWorth(LocalDate asOf, String currency, Money assets, Money liabilities, Money netWorth,
                           List<NetWorthGroup> groups) {
    }

    public Summary summary(UUID ownerId, LocalDate from, LocalDate to, String currency) {
        Period period = period(from, to);
        String target = currencyOf(currency);
        Period previous = previous(period);
        projection.ensureBuilt(ownerId);
        Totals now = totals(ownerId, period, target);
        Totals before = totals(ownerId, previous, target);
        BigDecimal savingsRate = now.income().isPositive() ? percent(now.net(), now.income()) : null;
        return new Summary(period, target, now, savingsRate, previous, before, change(now.income(), before.income()),
                change(now.expense(), before.expense()));
    }

    public CategoryReport categories(UUID ownerId, LocalDate from, LocalDate to, String type, String currency) {
        Period period = period(from, to);
        String target = currencyOf(currency);
        String kind = type == null ? EXPENSE : type;
        if (!INCOME.equals(kind) && !EXPENSE.equals(kind)) {
            throw new DomainException("INVALID_TYPE", "Type must be INCOME or EXPENSE");
        }
        projection.ensureBuilt(ownerId);
        Map<UUID, Money> byCategory = new HashMap<>();
        Money zero = Money.zero(Money.currency(target));
        for (DailyTotal t : store.dailyTotals(ownerId, period.from(), period.to())) {
            if (kind.equals(t.type())) {
                byCategory.merge(t.categoryId(), convert(ownerId, t, target, period), Money::plus);
            }
        }
        Map<UUID, CategoryView> categories = ledger.categories(ownerId).stream()
                .collect(Collectors.toMap(CategoryView::id, Function.identity()));
        Money total = byCategory.values().stream().reduce(zero, Money::plus);
        Map<UUID, List<CategoryTotal>> children = new LinkedHashMap<>();
        Map<UUID, Money> parents = new LinkedHashMap<>();
        byCategory.forEach((id, amount) -> {
            CategoryView category = categories.get(id);
            UUID parentId = category == null || category.parentId() == null ? id : category.parentId();
            parents.merge(parentId, amount, Money::plus);
            if (!parentId.equals(id)) {
                children.computeIfAbsent(parentId, k -> new ArrayList<>())
                        .add(new CategoryTotal(id, name(categories, id), amount, percent(amount, total), List.of()));
            }
        });
        List<CategoryTotal> result = parents.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), name(categories, e.getKey()), e.getValue(),
                        percent(e.getValue(), total), children.getOrDefault(e.getKey(), List.of()).stream()
                        .sorted(Comparator.comparing(CategoryTotal::amount).reversed()).toList()))
                .sorted(Comparator.comparing(CategoryTotal::amount).reversed())
                .toList();
        return new CategoryReport(period, kind, target, total, result);
    }

    public CashFlowReport cashFlow(UUID ownerId, LocalDate from, LocalDate to, Granularity granularity,
                                   String currency) {
        Period period = period(from, to);
        String target = currencyOf(currency);
        Granularity unit = granularity == null ? Granularity.MONTH : granularity;
        if (unit == Granularity.DAY && ChronoUnit.DAYS.between(period.from(), period.to()) >= MAX_DAILY_POINTS) {
            throw new DomainException("PERIOD_TOO_LONG", "Daily cash flow covers at most " + MAX_DAILY_POINTS
                    + " days");
        }
        projection.ensureBuilt(ownerId);
        Money zero = Money.zero(Money.currency(target));
        Map<String, Map<String, Money>> sums = new LinkedHashMap<>();
        for (String key : keys(period, unit)) {
            sums.put(key, new HashMap<>());
        }
        for (DailyTotal t : store.dailyTotals(ownerId, period.from(), period.to())) {
            Map<String, Money> point = sums.get(key(t.date(), unit));
            if (point != null) {
                point.merge(t.type(), convert(ownerId, t, target, period), Money::plus);
            }
        }
        List<CashFlowPoint> points = sums.entrySet().stream().map(e -> {
            Map<String, Money> p = e.getValue();
            Money income = p.getOrDefault(INCOME, zero);
            Money expense = p.getOrDefault(EXPENSE, zero);
            Money in = p.getOrDefault(EXTERNAL_IN, zero);
            Money out = p.getOrDefault(EXTERNAL_OUT, zero);
            return new CashFlowPoint(e.getKey(), income, expense, income.minus(expense), in, out,
                    income.plus(in).minus(expense).minus(out));
        }).toList();
        return new CashFlowReport(period, target, unit, points);
    }

    /** Wallets + savings + investments + deposits + money lent − money borrowed, in one currency. */
    public NetWorth netWorth(UUID ownerId, String currency) {
        String target = currencyOf(currency);
        NetWorthCalculator.Items items = netWorth.items(ownerId);
        Money zero = Money.zero(Money.currency(target));
        Map<Kind, List<NetWorthItem>> byKind = new EnumMap<>(Kind.class);
        for (Item item : items.items()) {
            Money converted = conversion.convert(ownerId, item.amount(), target, items.asOf());
            byKind.computeIfAbsent(item.kind(), k -> new ArrayList<>())
                    .add(new NetWorthItem(item.kind(), item.id(), item.name(), item.amount(), converted));
        }
        List<NetWorthGroup> groups = byKind.entrySet().stream()
                .map(e -> new NetWorthGroup(e.getKey(), e.getValue().stream().map(NetWorthItem::converted)
                        .reduce(zero, Money::plus), e.getValue()))
                .toList();
        Money liabilities = groups.stream().filter(g -> g.kind() == Kind.BORROWED).map(NetWorthGroup::total)
                .reduce(zero, Money::plus);
        Money assets = groups.stream().filter(g -> g.kind() != Kind.BORROWED).map(NetWorthGroup::total)
                .reduce(zero, Money::plus);
        return new NetWorth(items.asOf(), target, assets, liabilities, assets.minus(liabilities), groups);
    }

    /** Transactions of the period as CSV (UTF-8 with BOM so that spreadsheet programs show Vietnamese right). */
    public String csv(UUID ownerId, LocalDate from, LocalDate to) {
        Period period = period(from, to);
        projection.ensureBuilt(ownerId);
        Map<UUID, String> wallets = ledger.walletBalances(ownerId).stream()
                .collect(Collectors.toMap(WalletBalance::walletId, WalletBalance::name));
        Map<UUID, String> categories = ledger.categories(ownerId).stream()
                .collect(Collectors.toMap(CategoryView::id, CategoryView::name));
        StringBuilder csv = new StringBuilder("﻿date,type,wallet,category,amount,currency,source,excluded\n");
        for (ReportEntry e : store.entries(ownerId, period.from(), period.to())) {
            csv.append(e.date()).append(',').append(e.type()).append(',')
                    .append(cell(wallets.get(e.walletId()))).append(',')
                    .append(cell(e.categoryId() == null ? null : categories.get(e.categoryId()))).append(',')
                    .append(Money.of(e.amount(), e.currency()).roundedAmount().toPlainString()).append(',')
                    .append(e.currency()).append(',').append(cell(e.source())).append(',')
                    .append(e.excluded()).append('\n');
        }
        return csv.toString();
    }

    private Totals totals(UUID ownerId, Period period, String target) {
        Money income = Money.zero(Money.currency(target));
        Money expense = income;
        for (DailyTotal t : store.dailyTotals(ownerId, period.from(), period.to())) {
            if (INCOME.equals(t.type())) {
                income = income.plus(convert(ownerId, t, target, period));
            } else if (EXPENSE.equals(t.type())) {
                expense = expense.plus(convert(ownerId, t, target, period));
            }
        }
        return new Totals(income, expense, income.minus(expense));
    }

    private Money convert(UUID ownerId, DailyTotal total, String target, Period period) {
        LocalDate today = LocalDate.now(clock);
        LocalDate asOf = period.to().isAfter(today) ? today : period.to();
        return conversion.convert(ownerId, Money.of(total.amount(), total.currency()), target, asOf);
    }

    /** Defaults to the current month; at most ten years. */
    private Period period(LocalDate from, LocalDate to) {
        YearMonth month = YearMonth.now(clock);
        LocalDate start = from != null ? from : to != null ? to.withDayOfMonth(1) : month.atDay(1);
        LocalDate end = to != null ? to : from != null ? YearMonth.from(from).atEndOfMonth() : month.atEndOfMonth();
        if (end.isBefore(start)) {
            throw new DomainException("INVALID_PERIOD", "The end date is before the start date");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw new DomainException("PERIOD_TOO_LONG", "A report covers at most ten years");
        }
        return new Period(start, end);
    }

    static Period previous(Period period) {
        LocalDate from = period.from();
        LocalDate to = period.to();
        if (from.getDayOfMonth() == 1 && to.equals(YearMonth.from(to).atEndOfMonth())) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)) + 1;
            YearMonth start = YearMonth.from(from).minusMonths(months);
            return new Period(start.atDay(1), YearMonth.from(from).minusMonths(1).atEndOfMonth());
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new Period(from.minusDays(days), from.minusDays(1));
    }

    private static List<String> keys(Period period, Granularity unit) {
        List<String> keys = new ArrayList<>();
        if (unit == Granularity.DAY) {
            for (LocalDate d = period.from(); !d.isAfter(period.to()); d = d.plusDays(1)) {
                keys.add(d.toString());
            }
        } else {
            for (YearMonth m = YearMonth.from(period.from()); !m.isAfter(YearMonth.from(period.to()));
                 m = m.plusMonths(1)) {
                keys.add(m.toString());
            }
        }
        return keys;
    }

    private static String key(LocalDate date, Granularity unit) {
        return unit == Granularity.DAY ? date.toString() : YearMonth.from(date).toString();
    }

    private static String currencyOf(String currency) {
        Currency c = Money.currency(currency == null ? "VND" : currency);
        return c.getCurrencyCode();
    }

    private static BigDecimal percent(Money part, Money whole) {
        return whole.isZero() ? BigDecimal.ZERO
                : part.amount().multiply(HUNDRED).divide(whole.amount(), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal change(Money now, Money before) {
        return before.isZero() ? null : percent(now.minus(before), before);
    }

    private static String name(Map<UUID, CategoryView> categories, UUID id) {
        CategoryView category = id == null ? null : categories.get(id);
        return category == null ? null : category.name();
    }

    /** Quotes a CSV cell when needed and defuses spreadsheet formulas. */
    static String cell(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
