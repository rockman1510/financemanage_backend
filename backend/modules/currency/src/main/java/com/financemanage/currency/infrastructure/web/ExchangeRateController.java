package com.financemanage.currency.infrastructure.web;

import com.financemanage.currency.application.ExchangeRateService;
import com.financemanage.currency.infrastructure.web.ExchangeRateDtos.ConversionResponse;
import com.financemanage.currency.infrastructure.web.ExchangeRateDtos.ExchangeRateResponse;
import com.financemanage.currency.infrastructure.web.ExchangeRateDtos.SaveExchangeRateRequest;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exchange-rates")
class ExchangeRateController {

    private final ExchangeRateService service;
    private final Clock clock;

    ExchangeRateController(ExchangeRateService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** The user's own rates and the global ones, newest first. */
    @GetMapping
    List<ExchangeRateResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String base,
                                    @RequestParam(required = false) String quote) {
        return service.list(CurrentUser.id(jwt), base, quote).stream().map(ExchangeRateResponse::from).toList();
    }

    /** Creates the rate, or replaces it when the user already has one for that pair and date. */
    @PostMapping
    ExchangeRateResponse save(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SaveExchangeRateRequest request) {
        return ExchangeRateResponse.from(service.save(CurrentUser.id(jwt), request.base(), request.quote(),
                request.rate(), request.effectiveDate()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    @GetMapping("/convert")
    ConversionResponse convert(@AuthenticationPrincipal Jwt jwt, @RequestParam BigDecimal amount,
                               @RequestParam String from, @RequestParam String to,
                               @RequestParam(required = false) LocalDate date) {
        UUID owner = CurrentUser.id(jwt);
        LocalDate asOf = date != null ? date : LocalDate.now(clock);
        Money source = Money.of(amount, from);
        Currency target = Money.currency(to);
        BigDecimal rate = service.rate(owner, from, to, asOf)
                .orElseThrow(() -> new DomainException("EXCHANGE_RATE_MISSING",
                        "No exchange rate from " + from + " to " + to));
        Money converted = new Money(source.amount().multiply(rate), target).rounded();
        return new ConversionResponse(amount, from, to, ExchangeRateDtos.plain(rate), converted.roundedAmount(),
                asOf);
    }
}
