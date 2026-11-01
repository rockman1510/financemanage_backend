package com.financemanage.budget.infrastructure.web;

import com.financemanage.budget.application.BudgetService;
import com.financemanage.budget.infrastructure.web.BudgetDtos.BudgetResponse;
import com.financemanage.budget.infrastructure.web.BudgetDtos.CreateBudgetRequest;
import com.financemanage.budget.infrastructure.web.BudgetDtos.UpdateBudgetRequest;
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
@RequestMapping("/api/v1/budgets")
class BudgetController {

    private final BudgetService service;

    BudgetController(BudgetService service) {
        this.service = service;
    }

    /** Each budget with the status of the period containing {@code date} (today by default). */
    @GetMapping
    List<BudgetResponse> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) LocalDate date) {
        return service.list(CurrentUser.id(jwt), date).stream().map(BudgetResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BudgetResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBudgetRequest request) {
        return BudgetResponse.from(service.create(CurrentUser.id(jwt), request.toInput()));
    }

    @GetMapping("/{id}")
    BudgetResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                       @RequestParam(required = false) LocalDate date) {
        return BudgetResponse.from(service.get(id, CurrentUser.id(jwt), date));
    }

    @PatchMapping("/{id}")
    BudgetResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                          @Valid @RequestBody UpdateBudgetRequest request) {
        return BudgetResponse.from(service.update(id, CurrentUser.id(jwt), request.toChanges()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }
}
