package com.financemanage.ledger.infrastructure.web;

import static com.financemanage.ledger.infrastructure.web.WebAmounts.plain;

import com.financemanage.ledger.application.TransactionChanges;
import com.financemanage.ledger.application.TransactionInput;
import com.financemanage.ledger.application.TransferInput;
import com.financemanage.ledger.domain.model.Attachment;
import com.financemanage.ledger.domain.model.Transaction;
import com.financemanage.ledger.domain.model.Transaction.TransferLegs;
import com.financemanage.ledger.domain.model.TransactionTotals;
import com.financemanage.ledger.domain.model.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response contracts of the transaction and transfer REST API (v1). */
final class TransactionDtos {

    private TransactionDtos() {
    }

    /** The category decides whether it is an income or an expense; the amount is in the wallet's currency. */
    record CreateTransactionRequest(
            @NotNull UUID walletId,
            @NotNull UUID categoryId,
            @NotNull @Positive BigDecimal amount,
            LocalDate occurredOn,
            @Size(max = 500) String note,
            @Size(max = 120) String withPerson,
            Boolean excludeFromReport) {

        TransactionInput toInput() {
            return new TransactionInput(walletId, categoryId, amount, occurredOn, note, withPerson,
                    Boolean.TRUE.equals(excludeFromReport));
        }
    }

    /** Omitted fields stay unchanged; an empty note or person clears it. */
    record UpdateTransactionRequest(
            UUID walletId,
            UUID categoryId,
            @Positive BigDecimal amount,
            LocalDate occurredOn,
            @Size(max = 500) String note,
            @Size(max = 120) String withPerson,
            Boolean excludeFromReport) {

        TransactionChanges toChanges() {
            return new TransactionChanges(walletId, categoryId, amount, occurredOn, note, withPerson,
                    excludeFromReport);
        }
    }

    /** {@code receivedAmount} is only needed between wallets of different currencies (else the rate is used). */
    record TransferRequest(
            @NotNull UUID fromWalletId,
            @NotNull UUID toWalletId,
            @NotNull @Positive BigDecimal amount,
            @Positive BigDecimal receivedAmount,
            LocalDate occurredOn,
            @Size(max = 500) String note) {

        TransferInput toInput() {
            return new TransferInput(fromWalletId, toWalletId, amount, receivedAmount, occurredOn, note);
        }
    }

    record AttachmentResponse(UUID id, String fileName, String contentType, long size, Instant createdAt) {

        static AttachmentResponse from(Attachment a) {
            return new AttachmentResponse(a.id(), a.fileName(), a.contentType(), a.size(), a.createdAt());
        }
    }

    /** {@code attachments} is only filled by the detail endpoint (null in lists). */
    record TransactionResponse(UUID id, UUID walletId, TransactionType type, BigDecimal amount, String currency,
                               UUID categoryId, LocalDate occurredOn, String note, String withPerson,
                               boolean excludeFromReport, UUID transferId, UUID counterpartWalletId,
                               BigDecimal exchangeRate, UUID recurringId, Instant createdAt,
                               List<AttachmentResponse> attachments) {

        static TransactionResponse from(Transaction t) {
            return from(t, null);
        }

        static TransactionResponse from(Transaction t, List<Attachment> attachments) {
            return new TransactionResponse(t.getId(), t.getWalletId(), t.getType(), plain(t.getAmount()),
                    t.getAmount().currencyCode(), t.getCategoryId(), t.getOccurredOn(), t.getNote(),
                    t.getWithPerson(), t.isExcludeFromReport(), t.getTransferId(), t.getCounterpartWalletId(),
                    plain(t.getExchangeRate()), t.getRecurringId(),
                    t.getCreatedAt(),
                    attachments == null ? null : attachments.stream().map(AttachmentResponse::from).toList());
        }
    }

    record TransferResponse(UUID transferId, TransactionResponse out, TransactionResponse in) {

        static TransferResponse from(TransferLegs legs) {
            return new TransferResponse(legs.out().getTransferId(), TransactionResponse.from(legs.out()),
                    TransactionResponse.from(legs.in()));
        }
    }

    record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }

    record TotalsResponse(String currency, BigDecimal income, BigDecimal expense, BigDecimal net) {

        static TotalsResponse from(TransactionTotals t) {
            return new TotalsResponse(t.income().currencyCode(), plain(t.income()), plain(t.expense()),
                    plain(t.net()));
        }
    }
}
