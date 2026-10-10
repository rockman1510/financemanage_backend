package com.financemanage.loan.domain.model;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

public record Repayment(UUID id, RepaymentType type, LocalDate paidOn, Money amount, Money principalPortion,
                        Money interestPortion, Money feePortion, String idempotencyKey) {
}
