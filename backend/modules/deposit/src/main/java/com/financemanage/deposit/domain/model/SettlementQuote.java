package com.financemanage.deposit.domain.model;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;

/** What the owner receives when closing the deposit on {@code date}. */
public record SettlementQuote(LocalDate date, Money principal, Money interest, Money total, boolean early,
                              Money interestLostByEarlyWithdrawal) {
}
