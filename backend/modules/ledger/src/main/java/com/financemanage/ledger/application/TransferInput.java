package com.financemanage.ledger.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Input for a transfer. {@code receivedAmount} is only needed between wallets of different currencies; when it is
 * missing the owner's exchange rate is used.
 */
public record TransferInput(UUID fromWalletId, UUID toWalletId, BigDecimal amount, BigDecimal receivedAmount,
                            LocalDate occurredOn, String note) {
}
