package com.financemanage.loan.application;

import com.financemanage.loan.domain.model.Installment;
import com.financemanage.shared.domain.Money;
import java.util.List;

public record SchedulePreview(List<Installment> installments, Money totalInterest, Money totalPayment) {
}
