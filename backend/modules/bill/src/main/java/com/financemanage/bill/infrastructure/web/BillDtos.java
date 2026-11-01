package com.financemanage.bill.infrastructure.web;

import com.financemanage.bill.application.BillChanges;
import com.financemanage.bill.application.BillInput;
import com.financemanage.bill.application.PayBillInput;
import com.financemanage.bill.domain.model.Bill;
import com.financemanage.bill.domain.model.BillFrequency;
import com.financemanage.bill.domain.model.BillPayment;
import com.financemanage.bill.domain.model.PaymentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response contracts of the bill REST API (v1). */
final class BillDtos {

    private BillDtos() {
    }

    record CreateBillRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @Positive BigDecimal amount,
            @NotNull UUID walletId,
            @NotNull UUID categoryId,
            @NotNull BillFrequency frequency,
            @Min(1) @Max(99) Integer interval,
            @NotNull LocalDate firstDueDate,
            LocalDate endDate,
            @Min(0) @Max(30) Integer remindDaysBefore,
            @Size(max = 500) String note) {

        BillInput toInput() {
            return new BillInput(name, amount, walletId, categoryId, frequency, interval, firstDueDate, endDate,
                    remindDaysBefore, note);
        }
    }

    record UpdateBillRequest(@Size(max = 60) String name, @Positive BigDecimal amount, UUID walletId, UUID categoryId,
                             LocalDate endDate, @Min(0) @Max(30) Integer remindDaysBefore,
                             @Size(max = 500) String note, Boolean active) {

        BillChanges toChanges() {
            return new BillChanges(name, amount, walletId, categoryId, endDate, remindDaysBefore, note, active);
        }
    }

    /** Every field is optional: the bill's amount and wallet, and today, are used by default. */
    record PayBillRequest(@Positive BigDecimal amount, UUID walletId, LocalDate paidOn) {

        PayBillInput toInput() {
            return new PayBillInput(amount, walletId, paidOn);
        }
    }

    record PaymentResponse(UUID id, LocalDate dueDate, BigDecimal amount, UUID walletId, LocalDate paidOn,
                           PaymentStatus status, UUID transactionId, String failureReason) {

        static PaymentResponse from(BillPayment p) {
            return new PaymentResponse(p.getId(), p.getDueDate(),
                    p.getAmount() == null ? null : p.getAmount().roundedAmount(), p.getWalletId(), p.getPaidOn(),
                    p.getStatus(), p.getTransactionId(), p.getFailureReason());
        }
    }

    /** {@code nextDueDate}/{@code daysUntilDue} are null once the bill has ended; negative days = overdue. */
    record BillResponse(UUID id, String name, BigDecimal amount, String currency, UUID walletId, UUID categoryId,
                        BillFrequency frequency, int interval, LocalDate firstDueDate, LocalDate endDate,
                        int remindDaysBefore, String note, LocalDate nextDueDate, Long daysUntilDue, boolean overdue,
                        boolean active, List<PaymentResponse> payments) {

        static BillResponse from(Bill b, LocalDate today) {
            return new BillResponse(b.getId(), b.getName(), b.getAmount().roundedAmount(),
                    b.getAmount().currencyCode(), b.getWalletId(), b.getCategoryId(), b.getFrequency(),
                    b.getInterval(), b.getFirstDueDate(), b.getEndDate(), b.getRemindDaysBefore(), b.getNote(),
                    b.getNextDueDate(), b.daysUntilDue(today), b.isOverdue(today), b.isActive(),
                    b.getPayments().reversed().stream().map(PaymentResponse::from).toList());
        }
    }
}
