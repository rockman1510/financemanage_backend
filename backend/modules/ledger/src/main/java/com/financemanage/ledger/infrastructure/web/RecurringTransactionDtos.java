package com.financemanage.ledger.infrastructure.web;

import static com.financemanage.ledger.infrastructure.web.WebAmounts.plain;

import com.financemanage.ledger.application.RecurringChanges;
import com.financemanage.ledger.application.RecurringInput;
import com.financemanage.ledger.domain.model.Frequency;
import com.financemanage.ledger.domain.model.RecurringTransaction;
import com.financemanage.ledger.domain.model.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request/response contracts of the recurring transaction REST API (v1). */
final class RecurringTransactionDtos {

    private RecurringTransactionDtos() {
    }

    /** E.g. every 1 MONTHLY from 2025-01-05 = on the 5th of every month. */
    record CreateRecurringRequest(
            @NotNull UUID walletId,
            @NotNull UUID categoryId,
            @NotNull @Positive BigDecimal amount,
            @Size(max = 500) String note,
            @Size(max = 120) String withPerson,
            @NotNull Frequency frequency,
            @Min(1) @Max(99) Integer interval,
            @NotNull LocalDate startDate,
            LocalDate endDate) {

        RecurringInput toInput() {
            return new RecurringInput(walletId, categoryId, amount, note, withPerson, frequency,
                    interval == null ? 1 : interval, startDate, endDate);
        }
    }

    /** Omitted fields stay unchanged; {@code active} pauses or resumes the rule. */
    record UpdateRecurringRequest(
            UUID walletId,
            UUID categoryId,
            @Positive BigDecimal amount,
            @Size(max = 500) String note,
            @Size(max = 120) String withPerson,
            LocalDate endDate,
            Boolean active) {

        RecurringChanges toChanges() {
            return new RecurringChanges(walletId, categoryId, amount, note, withPerson, endDate, active);
        }
    }

    /** {@code nextOccurrence} is null once the rule has ended. */
    record RecurringResponse(UUID id, UUID walletId, UUID categoryId, TransactionType type, BigDecimal amount,
                             String currency, String note, String withPerson, Frequency frequency, int interval,
                             LocalDate startDate, LocalDate endDate, LocalDate nextOccurrence, int generatedCount,
                             boolean active) {

        static RecurringResponse from(RecurringTransaction r) {
            return new RecurringResponse(r.getId(), r.getWalletId(), r.getCategoryId(), r.getType(),
                    plain(r.getAmount()), r.getAmount().currencyCode(), r.getNote(), r.getWithPerson(),
                    r.getFrequency(), r.getInterval(), r.getStartDate(), r.getEndDate(), r.getNextOccurrence(),
                    r.getGeneratedCount(), r.isActive());
        }
    }
}
