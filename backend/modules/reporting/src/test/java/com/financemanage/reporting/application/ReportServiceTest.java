package com.financemanage.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.deposit.api.DepositQueryApi;
import com.financemanage.investment.api.InvestmentQueryApi;
import com.financemanage.investment.api.InvestmentQueryApi.Position;
import com.financemanage.ledger.api.CategoryView;
import com.financemanage.ledger.api.LedgerEvents.TransactionSnapshot;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.loan.api.LoanPosition;
import com.financemanage.loan.api.LoanQueryApi;
import com.financemanage.reporting.application.NetWorthCalculator.Kind;
import com.financemanage.reporting.application.ReportService.CashFlowPoint;
import com.financemanage.reporting.application.ReportService.CategoryReport;
import com.financemanage.reporting.application.ReportService.Granularity;
import com.financemanage.reporting.application.ReportService.NetWorth;
import com.financemanage.reporting.application.ReportService.Period;
import com.financemanage.reporting.application.ReportService.Summary;
import com.financemanage.reporting.domain.ReportEntry;
import com.financemanage.reporting.domain.ReportEntryStore;
import com.financemanage.saving.api.SavingQueryApi;
import com.financemanage.saving.api.SavingQueryApi.SavedAmount;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class ReportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 4, 20);
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID CASH = UUID.randomUUID();
    private static final UUID USD = UUID.randomUUID();
    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID CAFE = UUID.randomUUID(); // sub-category of FOOD
    private static final UUID RENT = UUID.randomUUID();
    private static final UUID SALARY = UUID.randomUUID();

    private final InMemoryStore store = new InMemoryStore();
    private final LedgerQueryApi ledger = mock(LedgerQueryApi.class);
    private final SavingQueryApi savings = mock(SavingQueryApi.class);
    private final InvestmentQueryApi investments = mock(InvestmentQueryApi.class);
    private final DepositQueryApi deposits = mock(DepositQueryApi.class);
    private final LoanQueryApi loans = mock(LoanQueryApi.class);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final ReportProjection projection = new ReportProjection(store, ledger);
    private final NetWorthCalculator netWorth = new NetWorthCalculator(ledger, savings, investments, deposits, loans,
            new ConcurrentMapCacheManager(NetWorthCalculator.CACHE), clock);
    private final ReportService reports = new ReportService(projection, store, ledger, new FakeConversion(),
            netWorth, clock);

    ReportServiceTest() {
        when(ledger.categories(OWNER)).thenReturn(List.of(new CategoryView(FOOD, "Ăn uống", "EXPENSE", null),
                new CategoryView(CAFE, "Cà phê", "EXPENSE", FOOD), new CategoryView(RENT, "Thuê nhà", "EXPENSE", null),
                new CategoryView(SALARY, "Lương", "INCOME", null)));
        when(ledger.walletBalances(OWNER)).thenReturn(List.of(
                new WalletBalance(CASH, "Tiền mặt", "CASH", Money.of(5_000_000, "VND"), false, false),
                new WalletBalance(USD, "Đô, la", "CASH", Money.of(100, "USD"), false, false)));
    }

    @Test
    void summaryComparesWithThePreviousMonthAndSkipsTransfersAndExcludedEntries() {
        record(CASH, "INCOME", 20_000_000, "VND", SALARY, "2025-04-05");
        record(CASH, "EXPENSE", 3_000_000, "VND", FOOD, "2025-04-06");
        record(USD, "EXPENSE", 40, "USD", RENT, "2025-04-07"); // 1 000 000 VND
        record(CASH, "TRANSFER_OUT", 1_000_000, "VND", null, "2025-04-08");
        record(CASH, "EXTERNAL_OUT", 2_000_000, "VND", null, "2025-04-09");
        projection.recorded(snapshot(UUID.randomUUID(), CASH, "EXPENSE", 999, "VND", FOOD, "2025-04-10", true));
        record(CASH, "EXPENSE", 2_000_000, "VND", FOOD, "2025-03-15");

        Summary s = reports.summary(OWNER, null, null, null);

        assertThat(s.period()).isEqualTo(new Period(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 30)));
        assertThat(s.totals().income()).isEqualTo(Money.of(20_000_000, "VND"));
        assertThat(s.totals().expense()).isEqualTo(Money.of(4_000_000, "VND"));
        assertThat(s.savingsRate()).isEqualByComparingTo("80");
        assertThat(s.previousPeriod()).isEqualTo(new Period(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)));
        assertThat(s.previous().expense()).isEqualTo(Money.of(2_000_000, "VND"));
        assertThat(s.expenseChange()).isEqualByComparingTo("100");
        assertThat(s.incomeChange()).isNull();
    }

    @Test
    void previousPeriodsKeepWholeMonthsOrTheSameNumberOfDays() {
        assertThat(ReportService.previous(new Period(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31))))
                .isEqualTo(new Period(LocalDate.of(2024, 10, 1), LocalDate.of(2024, 12, 31)));
        assertThat(ReportService.previous(new Period(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31))))
                .isEqualTo(new Period(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 28)));
        assertThat(ReportService.previous(new Period(LocalDate.of(2025, 3, 5), LocalDate.of(2025, 3, 14))))
                .isEqualTo(new Period(LocalDate.of(2025, 2, 23), LocalDate.of(2025, 3, 4)));
        assertThatThrownBy(() -> reports.summary(OWNER, TODAY, TODAY.minusDays(1), null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_PERIOD");
    }

    @Test
    void categoriesGroupSubCategoriesUnderTheirParent() {
        record(CASH, "EXPENSE", 3_000_000, "VND", FOOD, "2025-04-06");
        record(CASH, "EXPENSE", 1_000_000, "VND", CAFE, "2025-04-07");
        record(CASH, "EXPENSE", 4_000_000, "VND", RENT, "2025-04-08");

        CategoryReport report = reports.categories(OWNER, null, null, null, "VND");

        assertThat(report.total()).isEqualTo(Money.of(8_000_000, "VND"));
        assertThat(report.categories()).hasSize(2);
        assertThat(report.categories().getFirst().name()).isIn("Ăn uống", "Thuê nhà"); // both 4 000 000
        assertThat(report.categories()).anySatisfy(c -> {
            assertThat(c.name()).isEqualTo("Ăn uống");
            assertThat(c.amount()).isEqualTo(Money.of(4_000_000, "VND"));
            assertThat(c.percent()).isEqualByComparingTo("50");
            assertThat(c.children()).singleElement().satisfies(child -> {
                assertThat(child.name()).isEqualTo("Cà phê");
                assertThat(child.percent()).isEqualByComparingTo("12.5");
            });
        });
        assertThatThrownBy(() -> reports.categories(OWNER, null, null, "TRANSFER_IN", null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("INVALID_TYPE");
    }

    @Test
    void cashFlowHasEveryPeriodAndCountsExternalMovements() {
        record(CASH, "INCOME", 10_000_000, "VND", SALARY, "2025-02-05");
        record(CASH, "EXTERNAL_OUT", 3_000_000, "VND", null, "2025-02-06");
        record(CASH, "EXPENSE", 2_000_000, "VND", FOOD, "2025-04-06");

        List<CashFlowPoint> points = reports.cashFlow(OWNER, LocalDate.of(2025, 2, 1), LocalDate.of(2025, 4, 30),
                null, null).points();

        assertThat(points).extracting(CashFlowPoint::period).containsExactly("2025-02", "2025-03", "2025-04");
        assertThat(points.getFirst().net()).isEqualTo(Money.of(10_000_000, "VND"));
        assertThat(points.getFirst().cashFlow()).isEqualTo(Money.of(7_000_000, "VND"));
        assertThat(points.get(1).cashFlow().isZero()).isTrue();
        assertThat(points.get(2).net()).isEqualTo(Money.of(-2_000_000, "VND"));
        assertThat(reports.cashFlow(OWNER, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 4, 7), Granularity.DAY,
                null).points()).hasSize(7);
        assertThatThrownBy(() -> reports.cashFlow(OWNER, LocalDate.of(2024, 1, 1), TODAY, Granularity.DAY, null))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("PERIOD_TOO_LONG");
    }

    @Test
    void lateEventsNeverOverwriteNewerStateAndTheLedgerFillsTheReadModelOnce() {
        UUID id = UUID.randomUUID();
        projection.updated(snapshot(id, CASH, "EXPENSE", 500, "VND", FOOD, "2025-04-06", false));
        projection.recorded(snapshot(id, CASH, "EXPENSE", 100, "VND", FOOD, "2025-04-06", false));
        assertThat(store.find(id).orElseThrow().amount()).isEqualByComparingTo("500");

        UUID gone = UUID.randomUUID();
        projection.deleted(snapshot(gone, CASH, "EXPENSE", 100, "VND", FOOD, "2025-04-06", false));
        projection.recorded(snapshot(gone, CASH, "EXPENSE", 100, "VND", FOOD, "2025-04-06", false));
        projection.updated(snapshot(gone, CASH, "EXPENSE", 100, "VND", FOOD, "2025-04-06", false));
        assertThat(store.find(gone).orElseThrow().deleted()).isTrue();

        UUID old = UUID.randomUUID();
        when(ledger.transactions(OWNER)).thenReturn(List.of(
                snapshot(old, CASH, "EXPENSE", 700, "VND", FOOD, "2025-04-01", false),
                snapshot(gone, CASH, "EXPENSE", 100, "VND", FOOD, "2025-04-06", false)));
        assertThat(reports.summary(OWNER, null, null, null).totals().expense()).isEqualTo(Money.of(1200, "VND"));
        assertThat(store.built).containsExactly(OWNER);
    }

    @Test
    void netWorthConvertsEverythingAndSubtractsDebts() {
        when(savings.savedAmounts(OWNER)).thenReturn(List.of(new SavedAmount(UUID.randomUUID(), "Du lịch",
                Money.of(2_000_000, "VND"))));
        when(investments.positions(OWNER)).thenReturn(List.of(new Position(UUID.randomUUID(), "FPT", "STOCK",
                Money.of(10_000_000, "VND"), Money.of(8_000_000, "VND"))));
        when(loans.activePositions(OWNER, TODAY)).thenReturn(List.of(new LoanPosition(UUID.randomUUID(), "Ngân hàng",
                true, Money.of(4_000_000, "VND"), null, 0)));

        NetWorth n = reports.netWorth(OWNER, null);

        assertThat(n.assets()).isEqualTo(Money.of(5_000_000 + 2_500_000 + 2_000_000 + 10_000_000, "VND"));
        assertThat(n.liabilities()).isEqualTo(Money.of(4_000_000, "VND"));
        assertThat(n.netWorth()).isEqualTo(Money.of(15_500_000, "VND"));
        assertThat(n.groups()).extracting(ReportService.NetWorthGroup::kind)
                .containsExactly(Kind.WALLET, Kind.SAVING, Kind.INVESTMENT, Kind.BORROWED);
        when(savings.savedAmounts(OWNER)).thenReturn(List.of());
        assertThat(reports.netWorth(OWNER, "VND").netWorth()).isEqualTo(n.netWorth()); // cached until evicted
        netWorth.evict(OWNER);
        assertThat(reports.netWorth(OWNER, "VND").netWorth()).isEqualTo(Money.of(13_500_000, "VND"));
    }

    @Test
    void csvQuotesCellsAndDefusesFormulas() {
        record(USD, "EXPENSE", 40, "USD", RENT, "2025-04-07");

        String csv = reports.csv(OWNER, null, null);

        assertThat(csv).startsWith("﻿date,type,wallet,category,amount,currency,source,excluded\n")
                .contains("2025-04-07,EXPENSE,\"Đô, la\",Thuê nhà,40.00,USD,,false\n");
        assertThat(ReportService.cell("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(ReportService.cell("a\"b")).isEqualTo("\"a\"\"b\"");
    }

    private void record(UUID wallet, String type, long amount, String currency, UUID category, String date) {
        projection.recorded(snapshot(UUID.randomUUID(), wallet, type, amount, currency, category, date, false));
    }

    private static TransactionSnapshot snapshot(UUID id, UUID wallet, String type, long amount, String currency,
                                                UUID category, String date, boolean excluded) {
        return new TransactionSnapshot(id, OWNER, wallet, type, BigDecimal.valueOf(amount), BigDecimal.ZERO,
                currency, category, LocalDate.parse(date), excluded, null, null, null, null);
    }

    /** 1 USD = 25 000 VND. */
    private static class FakeConversion implements CurrencyConversionApi {
        @Override
        public Optional<BigDecimal> rate(UUID ownerId, String from, String to, LocalDate asOf) {
            return Optional.of(from.equals(to) ? BigDecimal.ONE : new BigDecimal("25000"));
        }

        @Override
        public Money convert(UUID ownerId, Money amount, String targetCurrency, LocalDate asOf) {
            return Money.of(amount.amount().multiply(rate(ownerId, amount.currencyCode(), targetCurrency, asOf)
                    .orElseThrow()), targetCurrency);
        }
    }

    private static class InMemoryStore implements ReportEntryStore {
        final Map<UUID, ReportEntry> entries = new LinkedHashMap<>();
        final Set<UUID> built = new HashSet<>();

        @Override
        public Optional<ReportEntry> find(UUID transactionId) {
            return Optional.ofNullable(entries.get(transactionId));
        }

        @Override
        public void save(ReportEntry entry) {
            entries.put(entry.transactionId(), entry);
        }

        @Override
        public List<DailyTotal> dailyTotals(UUID ownerId, LocalDate from, LocalDate to) {
            record Key(String type, String currency, UUID categoryId, LocalDate date) {
            }
            Map<Key, BigDecimal> sums = live(ownerId, from, to).stream().filter(e -> !e.excluded())
                    .collect(Collectors.toMap(e -> new Key(e.type(), e.currency(), e.categoryId(), e.date()),
                            ReportEntry::amount, BigDecimal::add, LinkedHashMap::new));
            List<DailyTotal> totals = new ArrayList<>();
            sums.forEach((k, v) -> totals.add(new DailyTotal(k.type(), k.currency(), k.categoryId(), k.date(), v)));
            return totals;
        }

        @Override
        public List<ReportEntry> entries(UUID ownerId, LocalDate from, LocalDate to) {
            return live(ownerId, from, to).stream().sorted(Comparator.comparing(ReportEntry::date)).toList();
        }

        @Override
        public boolean isBuilt(UUID ownerId) {
            return built.contains(ownerId);
        }

        @Override
        public void markBuilt(UUID ownerId) {
            built.add(ownerId);
        }

        @Override
        public void deleteByOwner(UUID ownerId) {
            entries.values().removeIf(e -> e.ownerId().equals(ownerId));
            built.remove(ownerId);
        }

        private List<ReportEntry> live(UUID ownerId, LocalDate from, LocalDate to) {
            return entries.values().stream().filter(e -> e.ownerId().equals(ownerId) && !e.deleted()
                    && !e.date().isBefore(from) && !e.date().isAfter(to)).toList();
        }
    }
}
