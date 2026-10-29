package com.financemanage.ledger.infrastructure.web;

import static com.financemanage.ledger.infrastructure.web.WebAmounts.plain;

import com.financemanage.ledger.application.WalletTotal;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.ledger.domain.model.Wallet.NewWallet;
import com.financemanage.ledger.domain.model.Wallet.WalletChanges;
import com.financemanage.ledger.domain.model.WalletType;
import com.financemanage.shared.domain.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Request/response contracts of the wallet REST API (v1). */
final class WalletDtos {

    private WalletDtos() {
    }

    record CreateWalletRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull WalletType type,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            BigDecimal initialBalance,
            @Size(max = 40) String icon,
            Boolean excludedFromTotal,
            @PositiveOrZero BigDecimal creditLimit,
            @Min(1) @Max(31) Integer statementDay,
            @Min(1) @Max(31) Integer paymentDueDay) {

        NewWallet toSpec(UUID ownerId) {
            String code = currency == null ? "VND" : currency;
            return new NewWallet(ownerId, name, type,
                    Money.of(initialBalance == null ? BigDecimal.ZERO : initialBalance, code), icon,
                    Boolean.TRUE.equals(excludedFromTotal), creditLimit == null ? null : Money.of(creditLimit, code),
                    statementDay,
                    paymentDueDay);
        }
    }

    record UpdateWalletRequest(
            @Size(max = 60) String name,
            @Size(max = 40) String icon,
            Boolean excludedFromTotal,
            Boolean archived,
            BigDecimal initialBalance,
            @PositiveOrZero BigDecimal creditLimit,
            @Min(1) @Max(31) Integer statementDay,
            @Min(1) @Max(31) Integer paymentDueDay) {

        WalletChanges toChanges() {
            return new WalletChanges(name, icon, excludedFromTotal, archived, initialBalance, creditLimit,
                    statementDay, paymentDueDay);
        }
    }

    record AdjustBalanceRequest(@NotNull BigDecimal newBalance, LocalDate occurredOn, @Size(max = 500) String note) {
    }

    record WalletResponse(UUID id, String name, WalletType type, String currency, BigDecimal initialBalance,
                          BigDecimal balance, boolean excludedFromTotal, boolean archived, String icon,
                          BigDecimal creditLimit, BigDecimal availableCredit, Integer statementDay,
                          Integer paymentDueDay) {

        static WalletResponse from(Wallet w) {
            return new WalletResponse(w.getId(), w.getName(), w.getType(), w.currencyCode(),
                    plain(w.getInitialBalance()), plain(w.getBalance()), w.isExcludedFromTotal(), w.isArchived(),
                    w.getIcon(), plain(w.getCreditLimit()), plain(w.availableCredit()), w.getStatementDay(),
                    w.getPaymentDueDay());
        }
    }

    record AdjustBalanceResponse(WalletResponse wallet, TransactionDtos.TransactionResponse transaction) {
    }

    record WalletTotalLine(UUID walletId, String name, BigDecimal balance, String currency,
                           BigDecimal convertedBalance) {
    }

    record WalletTotalResponse(String currency, BigDecimal total, List<WalletTotalLine> wallets,
                               Set<String> missingRates) {

        static WalletTotalResponse from(WalletTotal total) {
            return new WalletTotalResponse(total.total().currencyCode(), plain(total.total()),
                    total.wallets().stream().map(line -> new WalletTotalLine(line.wallet().getId(),
                            line.wallet().getName(), plain(line.wallet().getBalance()), line.wallet().currencyCode(),
                            plain(line.converted()))).toList(),
                    total.missingRates());
        }
    }
}
