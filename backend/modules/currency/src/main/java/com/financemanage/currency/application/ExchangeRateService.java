package com.financemanage.currency.application;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.currency.application.ExchangeRateProvider.ProvidedRate;
import com.financemanage.currency.domain.ExchangeRate;
import com.financemanage.currency.domain.ExchangeRateRepository;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exchange rate use cases. Also serves the module's public {@link CurrencyConversionApi}. */
@Service
@Transactional
public class ExchangeRateService implements CurrencyConversionApi {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final int LIST_LIMIT = 200;

    private final ExchangeRateRepository repository;
    private final List<ExchangeRateProvider> providers;
    private final Clock clock;

    public ExchangeRateService(ExchangeRateRepository repository, List<ExchangeRateProvider> providers, Clock clock) {
        this.repository = repository;
        this.providers = providers;
        this.clock = clock;
    }

    /** Creates the owner's rate for that pair and date, or replaces its value when it already exists. */
    public ExchangeRate save(UUID ownerId, String base, String quote, BigDecimal rate, LocalDate effectiveDate) {
        Currency baseCurrency = Money.currency(base);
        Currency quoteCurrency = Money.currency(quote);
        LocalDate date = effectiveDate != null ? effectiveDate : LocalDate.now(clock);
        ExchangeRate stored = repository.findExact(ownerId, base, quote, date)
                .map(existing -> {
                    existing.changeRate(rate);
                    return existing;
                })
                .orElseGet(() -> ExchangeRate.manual(ownerId, baseCurrency, quoteCurrency, rate, date));
        return repository.save(stored);
    }

    @Transactional(readOnly = true)
    public List<ExchangeRate> list(UUID ownerId, String base, String quote) {
        return repository.findVisible(ownerId, base, quote, LIST_LIMIT);
    }

    public void delete(UUID id, UUID ownerId) {
        repository.delete(repository.findByIdAndOwner(id, ownerId)
                .orElseThrow(() -> new NotFoundException("ExchangeRate", id)));
    }

    /** Stores today's rates of every registered provider as global rates. A failing provider does not stop others. */
    public int refreshFromProviders(LocalDate date) {
        int stored = 0;
        for (ExchangeRateProvider provider : providers) {
            try {
                for (ProvidedRate provided : provider.fetch(date)) {
                    storeGlobal(provider.name(), provided, date);
                    stored++;
                }
            } catch (RuntimeException e) {
                log.warn("Exchange rate provider {} failed: {}", provider.name(), e.getMessage());
            }
        }
        return stored;
    }

    private void storeGlobal(String source, ProvidedRate provided, LocalDate date) {
        ExchangeRate rate = repository.findExact(null, provided.base(), provided.quote(), date)
                .map(existing -> {
                    existing.changeRate(provided.rate());
                    existing.changeSource(source);
                    return existing;
                })
                .orElseGet(() -> ExchangeRate.global(source, Money.currency(provided.base()),
                        Money.currency(provided.quote()), provided.rate(), date));
        repository.save(rate);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> rate(UUID ownerId, String from, String to, LocalDate asOf) {
        if (from.equals(to)) {
            return Optional.of(BigDecimal.ONE);
        }
        LocalDate date = asOf != null ? asOf : LocalDate.now(clock);
        return repository.findLatest(ownerId, from, to, date).map(ExchangeRate::getRate)
                .or(() -> repository.findLatest(ownerId, to, from, date).map(ExchangeRate::inverse))
                .or(() -> repository.findLatest(null, from, to, date).map(ExchangeRate::getRate))
                .or(() -> repository.findLatest(null, to, from, date).map(ExchangeRate::inverse));
    }

    @Override
    @Transactional(readOnly = true)
    public Money convert(UUID ownerId, Money amount, String targetCurrency, LocalDate asOf) {
        Currency target = Money.currency(targetCurrency);
        BigDecimal rate = rate(ownerId, amount.currencyCode(), targetCurrency, asOf)
                .orElseThrow(() -> new DomainException("EXCHANGE_RATE_MISSING",
                        "No exchange rate from " + amount.currencyCode() + " to " + targetCurrency));
        return new Money(amount.amount().multiply(rate), target).rounded();
    }
}
