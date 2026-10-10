package com.financemanage.deposit.application;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;

/** Simulation of a deposit before it is opened. */
public record DepositPreview(LocalDate maturityDate, Money expectedInterest, Money totalAtMaturity) {
}
