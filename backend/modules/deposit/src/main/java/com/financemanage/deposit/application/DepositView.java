package com.financemanage.deposit.application;

import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.shared.domain.Money;

/** Deposit together with the interest figures computed by its calculation strategy. */
public record DepositView(Deposit deposit, Money accruedInterest, Money expectedInterestAtMaturity) {
}
