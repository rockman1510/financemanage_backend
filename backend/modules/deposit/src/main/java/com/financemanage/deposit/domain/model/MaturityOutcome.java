package com.financemanage.deposit.domain.model;

import com.financemanage.shared.domain.Money;
import java.time.LocalDate;

public record MaturityOutcome(LocalDate maturityDate, Money interest, boolean rolledOver, Money newPrincipal) {
}
