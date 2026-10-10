package com.financemanage.deposit.infrastructure.web;

import com.financemanage.deposit.application.DepositService;
import com.financemanage.deposit.domain.model.DepositStatus;
import com.financemanage.deposit.infrastructure.web.DepositDtos.CreateDepositRequest;
import com.financemanage.deposit.infrastructure.web.DepositDtos.DepositPreviewResponse;
import com.financemanage.deposit.infrastructure.web.DepositDtos.DepositResponse;
import com.financemanage.deposit.infrastructure.web.DepositDtos.SettleDepositRequest;
import com.financemanage.deposit.infrastructure.web.DepositDtos.SettlementQuoteResponse;
import com.financemanage.deposit.infrastructure.web.DepositDtos.UpdateDepositRequest;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/deposits")
class DepositController {

    private final DepositService service;

    DepositController(DepositService service) {
        this.service = service;
    }

    @GetMapping
    List<DepositResponse> list(@AuthenticationPrincipal Jwt jwt,
                               @RequestParam(required = false) DepositStatus status) {
        return service.list(CurrentUser.id(jwt), status).stream().map(DepositResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DepositResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateDepositRequest request) {
        return DepositResponse.from(service.open(request.toSpec(CurrentUser.id(jwt))));
    }

    @PostMapping("/preview")
    DepositPreviewResponse preview(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody CreateDepositRequest request) {
        return DepositPreviewResponse.from(service.preview(request.toSpec(CurrentUser.id(jwt))));
    }

    @GetMapping("/{id}")
    DepositResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return DepositResponse.from(service.get(id, CurrentUser.id(jwt)));
    }

    @PatchMapping("/{id}")
    DepositResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                           @Valid @RequestBody UpdateDepositRequest request) {
        return DepositResponse.from(service.updateDetails(id, CurrentUser.id(jwt), request.institutionName(),
                request.accountNumber(), request.note()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    @GetMapping("/{id}/settlement-quote")
    SettlementQuoteResponse quote(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @RequestParam(required = false) LocalDate date) {
        return SettlementQuoteResponse.from(service.settlementQuote(id, CurrentUser.id(jwt), date));
    }

    @PostMapping("/{id}/settle")
    SettlementQuoteResponse settle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @RequestBody(required = false) SettleDepositRequest request) {
        return SettlementQuoteResponse.from(
                service.settle(id, CurrentUser.id(jwt), request == null ? null : request.date()));
    }
}
