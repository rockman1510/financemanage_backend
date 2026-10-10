package com.financemanage.loan.infrastructure.web;

import com.financemanage.loan.application.LoanService;
import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import com.financemanage.loan.infrastructure.web.LoanDtos.CreateLoanRequest;
import com.financemanage.loan.infrastructure.web.LoanDtos.LoanDetailResponse;
import com.financemanage.loan.infrastructure.web.LoanDtos.LoanSummaryResponse;
import com.financemanage.loan.infrastructure.web.LoanDtos.PrepaymentRequest;
import com.financemanage.loan.infrastructure.web.LoanDtos.RateChangeRequest;
import com.financemanage.loan.infrastructure.web.LoanDtos.RepaymentRequest;
import com.financemanage.loan.infrastructure.web.LoanDtos.RepaymentResponse;
import com.financemanage.loan.infrastructure.web.LoanDtos.SchedulePreviewResponse;
import com.financemanage.loan.infrastructure.web.LoanDtos.UpdateLoanRequest;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
class LoanController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final LoanService service;
    private final Clock clock;

    LoanController(LoanService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    List<LoanSummaryResponse> list(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(required = false) LoanDirection direction,
                                   @RequestParam(required = false) LoanStatus status) {
        LocalDate today = today();
        return service.list(CurrentUser.id(jwt), direction, status).stream()
                .map(l -> LoanSummaryResponse.from(l, today)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    LoanDetailResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateLoanRequest request) {
        return detail(service.create(request.toSpec(CurrentUser.id(jwt))));
    }

    @PostMapping("/preview-schedule")
    SchedulePreviewResponse preview(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateLoanRequest request) {
        return SchedulePreviewResponse.from(service.previewSchedule(request.toSpec(CurrentUser.id(jwt))), today());
    }

    @GetMapping("/{id}")
    LoanDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return detail(service.get(id, CurrentUser.id(jwt)));
    }

    @PatchMapping("/{id}")
    LoanDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                              @Valid @RequestBody UpdateLoanRequest request) {
        return detail(service.updateDetails(id, CurrentUser.id(jwt), request.counterpartyName(), request.note()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    RepaymentResponse repay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                            @Valid @RequestBody RepaymentRequest request) {
        UUID owner = CurrentUser.id(jwt);
        Loan loan = service.get(id, owner);
        Money amount = new Money(request.amount(), loan.getPrincipal().currency());
        return RepaymentResponse.from(service.recordRepayment(id, owner, amount, request.paidOn(), idempotencyKey));
    }

    @PostMapping("/{id}/prepayments")
    @ResponseStatus(HttpStatus.CREATED)
    RepaymentResponse prepay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                             @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                             @Valid @RequestBody PrepaymentRequest request) {
        UUID owner = CurrentUser.id(jwt);
        Loan loan = service.get(id, owner);
        Money principal = new Money(request.principal(), loan.getPrincipal().currency());
        return RepaymentResponse.from(service.prepay(id, owner, principal, request.paidOn(), idempotencyKey));
    }

    @PostMapping("/{id}/rate-changes")
    LoanDetailResponse changeRate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody RateChangeRequest request) {
        return detail(service.changeRate(id, CurrentUser.id(jwt), request.effectiveFrom(),
                Percentage.of(request.annualRate())));
    }

    private LoanDetailResponse detail(Loan loan) {
        return LoanDetailResponse.from(loan, today());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
