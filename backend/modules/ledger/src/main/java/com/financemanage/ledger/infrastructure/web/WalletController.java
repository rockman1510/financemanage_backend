package com.financemanage.ledger.infrastructure.web;

import com.financemanage.ledger.application.TransactionService;
import com.financemanage.ledger.application.WalletService;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.infrastructure.web.TransactionDtos.TransactionResponse;
import com.financemanage.ledger.infrastructure.web.WalletDtos.AdjustBalanceRequest;
import com.financemanage.ledger.infrastructure.web.WalletDtos.AdjustBalanceResponse;
import com.financemanage.ledger.infrastructure.web.WalletDtos.CreateWalletRequest;
import com.financemanage.ledger.infrastructure.web.WalletDtos.UpdateWalletRequest;
import com.financemanage.ledger.infrastructure.web.WalletDtos.WalletResponse;
import com.financemanage.ledger.infrastructure.web.WalletDtos.WalletTotalResponse;
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
@RequestMapping("/api/v1/wallets")
class WalletController {

    private final WalletService wallets;
    private final TransactionService transactions;

    WalletController(WalletService wallets, TransactionService transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @GetMapping
    List<WalletResponse> list(@AuthenticationPrincipal Jwt jwt,
                              @RequestParam(defaultValue = "false") boolean includeArchived) {
        return wallets.list(CurrentUser.id(jwt), includeArchived).stream().map(WalletResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WalletResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateWalletRequest request) {
        return WalletResponse.from(wallets.open(request.toSpec(CurrentUser.id(jwt))));
    }

    /** Total balance of the wallets that count towards the total, converted into {@code currency}. */
    @GetMapping("/total")
    WalletTotalResponse total(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "VND") String currency,
                              @RequestParam(required = false) LocalDate date) {
        return WalletTotalResponse.from(wallets.total(CurrentUser.id(jwt), currency, date));
    }

    @GetMapping("/{id}")
    WalletResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return WalletResponse.from(wallets.get(id, CurrentUser.id(jwt)));
    }

    @PatchMapping("/{id}")
    WalletResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                          @Valid @RequestBody UpdateWalletRequest request) {
        return WalletResponse.from(wallets.update(id, CurrentUser.id(jwt), request.toChanges()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        wallets.delete(id, CurrentUser.id(jwt));
    }

    /** Records an adjustment transaction so the balance becomes {@code newBalance} (Điều chỉnh số dư). */
    @PostMapping("/{id}/adjust-balance")
    @ResponseStatus(HttpStatus.CREATED)
    AdjustBalanceResponse adjust(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @Valid @RequestBody AdjustBalanceRequest request) {
        UUID owner = CurrentUser.id(jwt);
        Transaction adjustment = transactions.adjustBalance(id, owner, request.newBalance(), request.occurredOn(),
                request.note());
        return new AdjustBalanceResponse(WalletResponse.from(wallets.get(id, owner)),
                TransactionResponse.from(adjustment));
    }
}
