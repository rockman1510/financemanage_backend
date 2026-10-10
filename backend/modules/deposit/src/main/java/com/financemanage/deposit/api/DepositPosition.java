package com.financemanage.deposit.api;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

public record DepositPosition(UUID depositId, String institutionName, Money principal, Money accruedInterest,
                              LocalDate maturityDate) {
}
