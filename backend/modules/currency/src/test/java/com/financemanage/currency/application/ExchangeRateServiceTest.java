package com.financemanage.currency.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financemanage.currency.domain.ExchangeRate;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExchangeRateServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 4, 1);
    private static final UUID OWNER = UUID.randomUUID();

    private final InMemoryExchangeRateRepository repository = new InMemoryExchangeRateRepository();
    private final List<ExchangeRateProvider> providers = new ArrayList<>();
    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ExchangeRateService(repository, providers, clock);
    }

    @Test
    void savingTheSamePairAndDateReplacesTheRate() {
        service.save(OWNER, "USD", "VND", new BigDecimal("25000"), null);
        ExchangeRate updated = service.save(OWNER, "USD", "VND", new BigDecimal("25500"), null);

        assertThat(repository.store).hasSize(1);
        assertThat(updated.getEffectiveDate()).isEqualTo(TODAY);
        assertThat(updated.getRate()).isEqualByComparingTo("25500");
    }

    @Test
    void usesTheLatestRateEffectiveOnTheDate() {
        service.save(OWNER, "USD", "VND", new BigDecimal("24000"), TODAY.minusMonths(2));
        service.save(OWNER, "USD", "VND", new BigDecimal("25000"), TODAY.minusDays(1));
        service.save(OWNER, "USD", "VND", new BigDecimal("26000"), TODAY.plusDays(5));

        assertThat(service.rate(OWNER, "USD", "VND", TODAY)).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("25000"));
        assertThat(service.rate(OWNER, "USD", "VND", TODAY.minusMonths(1))).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("24000"));
        assertThat(service.rate(OWNER, "USD", "VND", TODAY.minusYears(1))).isEmpty();
    }

    @Test
    void ownRatesWinOverGlobalOnesAndInversePairsAreUsed() {
        providers.add(provider("VCB", new ExchangeRateProvider.ProvidedRate("USD", "VND", new BigDecimal("25400"))));
        service.refreshFromProviders(TODAY);

        UUID other = UUID.randomUUID();
        assertThat(service.rate(other, "USD", "VND", TODAY)).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("25400"));
        assertThat(service.rate(other, "VND", "USD", TODAY)).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("0.0000393701"));

        service.save(OWNER, "VND", "USD", new BigDecimal("0.00004"), TODAY);
        assertThat(service.rate(OWNER, "USD", "VND", TODAY)).hasValueSatisfying(
                r -> assertThat(r).isEqualByComparingTo("25000"));
        assertThat(service.rate(OWNER, "EUR", "EUR", TODAY)).hasValue(BigDecimal.ONE);
    }

    @Test
    void convertsAndRoundsToTheTargetCurrency() {
        service.save(OWNER, "USD", "VND", new BigDecimal("25432.1"), TODAY);

        assertThat(service.convert(OWNER, Money.of("10.25", "USD"), "VND", TODAY))
                .isEqualTo(Money.of("260679", "VND"));
        assertThatThrownBy(() -> service.convert(OWNER, Money.of(1, "EUR"), "VND", TODAY))
                .isInstanceOf(DomainException.class).extracting("code").isEqualTo("EXCHANGE_RATE_MISSING");
    }

    @Test
    void failingProviderDoesNotStopTheOthers() {
        providers.add(new ExchangeRateProvider() {
            @Override
            public String name() {
                return "BROKEN";
            }

            @Override
            public List<ProvidedRate> fetch(LocalDate date) {
                throw new IllegalStateException("timeout");
            }
        });
        providers.add(provider("VCB", new ExchangeRateProvider.ProvidedRate("EUR", "VND", new BigDecimal("27000"))));

        assertThat(service.refreshFromProviders(TODAY)).isEqualTo(1);
        assertThat(service.refreshFromProviders(TODAY)).isEqualTo(1); // same day again updates, no duplicate
        assertThat(repository.store).hasSize(1);
    }

    @Test
    void usersCanOnlyDeleteTheirOwnRates() {
        providers.add(provider("VCB", new ExchangeRateProvider.ProvidedRate("USD", "VND", new BigDecimal("25400"))));
        service.refreshFromProviders(TODAY);
        ExchangeRate global = repository.store.values().iterator().next();
        ExchangeRate own = service.save(OWNER, "USD", "VND", new BigDecimal("25000"), TODAY);

        assertThat(service.list(OWNER, "USD", null)).hasSize(2);
        assertThatThrownBy(() -> service.delete(global.getId(), OWNER)).isInstanceOf(NotFoundException.class);
        service.delete(own.getId(), OWNER);
        assertThat(service.list(OWNER, null, null)).containsExactly(global);
    }

    private static ExchangeRateProvider provider(String name, ExchangeRateProvider.ProvidedRate... rates) {
        return new ExchangeRateProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<ProvidedRate> fetch(LocalDate date) {
                return List.of(rates);
            }
        };
    }
}
