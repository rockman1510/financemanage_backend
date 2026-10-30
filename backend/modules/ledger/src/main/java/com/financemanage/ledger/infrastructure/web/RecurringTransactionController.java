package com.financemanage.ledger.infrastructure.web;

import com.financemanage.ledger.application.RecurringTransactionService;
import com.financemanage.ledger.infrastructure.web.RecurringTransactionDtos.CreateRecurringRequest;
import com.financemanage.ledger.infrastructure.web.RecurringTransactionDtos.RecurringResponse;
import com.financemanage.ledger.infrastructure.web.RecurringTransactionDtos.UpdateRecurringRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recurring-transactions")
class RecurringTransactionController {

    private final RecurringTransactionService service;

    RecurringTransactionController(RecurringTransactionService service) {
        this.service = service;
    }

    @GetMapping
    List<RecurringResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt)).stream().map(RecurringResponse::from).toList();
    }

    /** Occurrences up to today are recorded immediately; later ones by the daily job. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RecurringResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRecurringRequest request) {
        return RecurringResponse.from(service.create(CurrentUser.id(jwt), request.toInput()));
    }

    @GetMapping("/{id}")
    RecurringResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return RecurringResponse.from(service.get(id, CurrentUser.id(jwt)));
    }

    @PatchMapping("/{id}")
    RecurringResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                             @Valid @RequestBody UpdateRecurringRequest request) {
        return RecurringResponse.from(service.update(id, CurrentUser.id(jwt), request.toChanges()));
    }

    /** Stops the rule; transactions it already recorded are kept. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }
}
