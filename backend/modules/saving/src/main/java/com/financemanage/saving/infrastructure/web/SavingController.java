package com.financemanage.saving.infrastructure.web;

import com.financemanage.saving.application.SavingService;
import com.financemanage.saving.domain.model.SavingGoal;
import com.financemanage.saving.infrastructure.web.SavingDtos.AutoContributionRequest;
import com.financemanage.saving.infrastructure.web.SavingDtos.CreateGoalRequest;
import com.financemanage.saving.infrastructure.web.SavingDtos.GoalResponse;
import com.financemanage.saving.infrastructure.web.SavingDtos.MoveRequest;
import com.financemanage.saving.infrastructure.web.SavingDtos.MovementResponse;
import com.financemanage.saving.infrastructure.web.SavingDtos.RoundUpRequest;
import com.financemanage.saving.infrastructure.web.SavingDtos.UpdateGoalRequest;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.Clock;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/saving-goals")
class SavingController {

    private final SavingService service;
    private final Clock clock;

    SavingController(SavingService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    List<GoalResponse> list(@AuthenticationPrincipal Jwt jwt) {
        LocalDate today = LocalDate.now(clock);
        return service.list(CurrentUser.id(jwt)).stream().map(g -> GoalResponse.from(g, today)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GoalResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateGoalRequest request) {
        return response(service.create(CurrentUser.id(jwt), request.name(), request.target(), request.currency(),
                request.deadline(), request.icon()));
    }

    @GetMapping("/{id}")
    GoalResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return response(service.get(id, CurrentUser.id(jwt)));
    }

    @PatchMapping("/{id}")
    GoalResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                        @Valid @RequestBody UpdateGoalRequest request) {
        return response(service.update(id, CurrentUser.id(jwt), request.name(), request.target(), request.deadline(),
                Boolean.TRUE.equals(request.clearDeadline()), request.icon()));
    }

    /** Only an empty goal can be deleted. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    /** Moves money from a wallet into the goal; 202, PENDING until the ledger records the wallet side. */
    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MovementResponse deposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                             @Valid @RequestBody MoveRequest request) {
        return MovementResponse.from(service.deposit(id, CurrentUser.id(jwt), request.amount(), request.walletId(),
                request.date()));
    }

    /** Moves money from the goal back to a wallet; 202, PENDING until the ledger records it. */
    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.ACCEPTED)
    MovementResponse withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody MoveRequest request) {
        return MovementResponse.from(service.withdraw(id, CurrentUser.id(jwt), request.amount(), request.walletId(),
                request.date()));
    }

    /** Contributes automatically every week or month from the start date (today by default, at once if due). */
    @PutMapping("/{id}/auto-contribution")
    GoalResponse setAutoContribution(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody AutoContributionRequest request) {
        return response(service.setAutoContribution(id, CurrentUser.id(jwt), request.amount(), request.walletId(),
                request.frequency(), request.startDate()));
    }

    @DeleteMapping("/{id}/auto-contribution")
    GoalResponse stopAutoContribution(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return response(service.stopAutoContribution(id, CurrentUser.id(jwt)));
    }

    @PutMapping("/{id}/round-up")
    GoalResponse setRoundUp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @Valid @RequestBody RoundUpRequest request) {
        return response(service.setRoundUp(id, CurrentUser.id(jwt), request.walletId(), request.unit()));
    }

    @DeleteMapping("/{id}/round-up")
    GoalResponse stopRoundUp(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return response(service.stopRoundUp(id, CurrentUser.id(jwt)));
    }

    private GoalResponse response(SavingGoal goal) {
        return GoalResponse.from(goal, LocalDate.now(clock));
    }
}
