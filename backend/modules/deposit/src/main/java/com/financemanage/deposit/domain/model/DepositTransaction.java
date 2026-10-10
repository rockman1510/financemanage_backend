package com.financemanage.deposit.domain.model;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

public record DepositTransaction(UUID id, DepositTransactionType type, Money amount, LocalDate occurredOn) {

    public static DepositTransaction of(DepositTransactionType type, Money amount, LocalDate occurredOn) {
        return new DepositTransaction(UUID.randomUUID(), type, amount, occurredOn);
    }
}
