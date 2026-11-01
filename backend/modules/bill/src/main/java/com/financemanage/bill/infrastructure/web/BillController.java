package com.financemanage.bill.infrastructure.web;

import com.financemanage.bill.application.BillService;
import com.financemanage.bill.infrastructure.web.BillDtos.BillResponse;
import com.financemanage.bill.infrastructure.web.BillDtos.CreateBillRequest;
import com.financemanage.bill.infrastructure.web.BillDtos.PayBillRequest;
import com.financemanage.bill.infrastructure.web.BillDtos.PaymentResponse;
import com.financemanage.bill.infrastructure.web.BillDtos.UpdateBillRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bills")
class BillController {

    private final BillService service;
    private final Clock clock;

    BillController(BillService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    /** All bills, the next due first. */
    @GetMapping
    List<BillResponse> list(@AuthenticationPrincipal Jwt jwt) {
        LocalDate today = LocalDate.now(clock);
        return service.list(CurrentUser.id(jwt)).stream().map(b -> BillResponse.from(b, today)).toList();
    }

    /** Bills overdue or due within {@code days} days. */
    @GetMapping("/upcoming")
    List<BillResponse> upcoming(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "30") int days) {
        LocalDate today = LocalDate.now(clock);
        return service.upcoming(CurrentUser.id(jwt), Math.clamp(days, 0, 366)).stream()
                .map(b -> BillResponse.from(b, today)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BillResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBillRequest request) {
        return BillResponse.from(service.create(CurrentUser.id(jwt), request.toInput()), LocalDate.now(clock));
    }

    @GetMapping("/{id}")
    BillResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return BillResponse.from(service.get(id, CurrentUser.id(jwt)), LocalDate.now(clock));
    }

    @PatchMapping("/{id}")
    BillResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                        @Valid @RequestBody UpdateBillRequest request) {
        return BillResponse.from(service.update(id, CurrentUser.id(jwt), request.toChanges()), LocalDate.now(clock));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    /**
     * Pays the current due date. Answers 202: the payment is PENDING until the ledger records the expense (a moment
     * later, then RECORDED – or FAILED, and the due date comes back).
     */
    @PostMapping("/{id}/pay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PaymentResponse pay(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                        @Valid @RequestBody(required = false) PayBillRequest request) {
        PayBillRequest body = request != null ? request : new PayBillRequest(null, null, null);
        return PaymentResponse.from(service.pay(id, CurrentUser.id(jwt), body.toInput()));
    }

    /** Skips the current due date without paying. */
    @PostMapping("/{id}/skip")
    PaymentResponse skip(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return PaymentResponse.from(service.skip(id, CurrentUser.id(jwt)));
    }
}
