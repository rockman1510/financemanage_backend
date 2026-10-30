package com.financemanage.investment.infrastructure.web;

import com.financemanage.investment.application.InvestmentService;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.AssetResponse;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.CreateAssetRequest;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.IncomeRequest;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.PortfolioResponse;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.PriceRequest;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.TradeRequest;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.TradeResponse;
import com.financemanage.investment.infrastructure.web.InvestmentDtos.UpdateAssetRequest;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investments")
class InvestmentController {

    private final InvestmentService service;

    InvestmentController(InvestmentService service) {
        this.service = service;
    }

    @GetMapping
    List<AssetResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt)).stream().map(AssetResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AssetResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAssetRequest request) {
        return AssetResponse.from(service.create(CurrentUser.id(jwt), request.name(), request.symbol(),
                request.type(), request.currency(), request.note()));
    }

    /** Totals of every asset in one currency (VND by default) and the split by asset type. */
    @GetMapping("/portfolio")
    PortfolioResponse portfolio(@AuthenticationPrincipal Jwt jwt,
                                @RequestParam(required = false) String currency) {
        return PortfolioResponse.from(service.portfolio(CurrentUser.id(jwt), currency));
    }

    @GetMapping("/{id}")
    AssetResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return AssetResponse.from(service.get(id, CurrentUser.id(jwt)));
    }

    @PatchMapping("/{id}")
    AssetResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                         @Valid @RequestBody UpdateAssetRequest request) {
        return AssetResponse.from(service.update(id, CurrentUser.id(jwt), request.name(), request.symbol(),
                request.note()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    /** With a wallet the cash is PENDING until the ledger records it (EXTERNAL_OUT). */
    @PostMapping("/{id}/buys")
    @ResponseStatus(HttpStatus.CREATED)
    TradeResponse buy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                      @Valid @RequestBody TradeRequest request) {
        return TradeResponse.from(service.buy(id, CurrentUser.id(jwt), request.quantity(), request.price(),
                request.fee(), request.walletId(), request.date()));
    }

    @PostMapping("/{id}/sells")
    @ResponseStatus(HttpStatus.CREATED)
    TradeResponse sell(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                       @Valid @RequestBody TradeRequest request) {
        return TradeResponse.from(service.sell(id, CurrentUser.id(jwt), request.quantity(), request.price(),
                request.fee(), request.walletId(), request.date()));
    }

    @PostMapping("/{id}/incomes")
    @ResponseStatus(HttpStatus.CREATED)
    TradeResponse income(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                         @Valid @RequestBody IncomeRequest request) {
        return TradeResponse.from(service.income(id, CurrentUser.id(jwt), request.amount(), request.walletId(),
                request.categoryId(), request.date()));
    }

    @PutMapping("/{id}/price")
    AssetResponse price(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                        @Valid @RequestBody PriceRequest request) {
        return AssetResponse.from(service.updatePrice(id, CurrentUser.id(jwt), request.price(), request.date()));
    }
}
