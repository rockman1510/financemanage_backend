package com.financemanage.ledger.infrastructure.web;

import static com.financemanage.ledger.infrastructure.web.WebAmounts.IDEMPOTENCY_KEY;
import static com.financemanage.ledger.infrastructure.web.WebAmounts.idempotencyKey;

import com.financemanage.ledger.application.TransactionService;
import com.financemanage.ledger.application.TransactionView;
import com.financemanage.ledger.domain.model.PageResult;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.TransactionFilter;
import com.financemanage.ledger.domain.model.TransactionType;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.CreateTransactionRequest;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.PageResponse;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.TotalsResponse;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.TransactionResponse;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.TransferRequest;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.TransferResponse;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.UpdateTransactionRequest;
import com.financemanage.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService service;

    TransactionController(TransactionService service) {
        this.service = service;
    }

    /** Newest first. {@code categoryId} also matches sub-categories; repeat a parameter to pass several values. */
    @SuppressWarnings("java:S107")
    @GetMapping("/api/v1/transactions")
    PageResponse<TransactionResponse> search(@AuthenticationPrincipal Jwt jwt,
                                             @RequestParam(required = false) UUID walletId,
                                             @RequestParam(required = false) List<UUID> categoryId,
                                             @RequestParam(required = false) List<TransactionType> type,
                                             @RequestParam(required = false) LocalDate from,
                                             @RequestParam(required = false) LocalDate to,
                                             @RequestParam(required = false) BigDecimal minAmount,
                                             @RequestParam(required = false) BigDecimal maxAmount,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        TransactionFilter filter = filter(jwt, walletId, categoryId, type, from, to, minAmount, maxAmount, q);
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageResult<Transaction> result = service.search(filter, pageIndex, pageSize);
        return new PageResponse<>(result.items().stream().map(TransactionResponse::from).toList(), pageIndex,
                pageSize, result.total(), (int) ((result.total() + pageSize - 1) / pageSize));
    }

    /** Income, expense and net per currency for the same filters (transfers and adjustments never count). */
    @SuppressWarnings("java:S107")
    @GetMapping("/api/v1/transactions/summary")
    List<TotalsResponse> summary(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) UUID walletId,
                                 @RequestParam(required = false) List<UUID> categoryId,
                                 @RequestParam(required = false) List<TransactionType> type,
                                 @RequestParam(required = false) LocalDate from,
                                 @RequestParam(required = false) LocalDate to,
                                 @RequestParam(required = false) BigDecimal minAmount,
                                 @RequestParam(required = false) BigDecimal maxAmount,
                                 @RequestParam(required = false) String q) {
        return service.totals(filter(jwt, walletId, categoryId, type, from, to, minAmount, maxAmount, q)).stream()
                .map(TotalsResponse::from).toList();
    }

    @PostMapping("/api/v1/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse create(@AuthenticationPrincipal Jwt jwt,
                               @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                               @Valid @RequestBody CreateTransactionRequest request) {
        return TransactionResponse.from(service.record(CurrentUser.id(jwt), request.toInput(), idempotencyKey(key)));
    }

    @GetMapping("/api/v1/transactions/{id}")
    TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        TransactionView view = service.get(id, CurrentUser.id(jwt));
        return TransactionResponse.from(view.transaction(), view.attachments());
    }

    @PatchMapping("/api/v1/transactions/{id}")
    TransactionResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                               @Valid @RequestBody UpdateTransactionRequest request) {
        return TransactionResponse.from(service.update(id, CurrentUser.id(jwt), request.toChanges()));
    }

    /** Deleting one leg of a transfer deletes the whole transfer. */
    @DeleteMapping("/api/v1/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(id, CurrentUser.id(jwt));
    }

    @PostMapping("/api/v1/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    TransferResponse transfer(@AuthenticationPrincipal Jwt jwt,
                              @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
                              @Valid @RequestBody TransferRequest request) {
        return TransferResponse.from(service.transfer(CurrentUser.id(jwt), request.toInput(), idempotencyKey(key)));
    }

    @DeleteMapping("/api/v1/transfers/{transferId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTransfer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID transferId) {
        service.deleteTransfer(CurrentUser.id(jwt), transferId);
    }

    @SuppressWarnings("java:S107")
    private static TransactionFilter filter(Jwt jwt, UUID walletId, List<UUID> categoryIds,
                                            List<TransactionType> types, LocalDate from, LocalDate to,
                                            BigDecimal minAmount, BigDecimal maxAmount, String text) {
        return new TransactionFilter(CurrentUser.id(jwt), walletId,
                categoryIds == null ? null : new HashSet<>(categoryIds), types == null ? null : new HashSet<>(types),
                from, to, minAmount, maxAmount, text);
    }
}
