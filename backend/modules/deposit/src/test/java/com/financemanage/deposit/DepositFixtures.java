package com.financemanage.deposit;

import com.financemanage.deposit.domain.model.DepositType;
import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.deposit.domain.model.InterestPaymentMethod;
import com.financemanage.deposit.domain.model.NewDeposit;
import com.financemanage.deposit.domain.model.RolloverPolicy;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import java.util.UUID;

public final class DepositFixtures {

    public static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final LocalDate OPEN_DATE = LocalDate.of(2025, 1, 1);

    private DepositFixtures() {
    }

    public static NewDeposit termDeposit(RolloverPolicy rollover) {
        return new NewDeposit(OWNER, "Vietcombank", "123", DepositType.TERM, Money.of(100_000_000, "VND"),
                Percentage.of("6"), Percentage.of("0.5"), 6, OPEN_DATE, InterestPaymentMethod.AT_MATURITY, rollover,
                InterestCalculationMethod.SIMPLE_ACTUAL_365, null);
    }

    public static NewDeposit demandDeposit() {
        return new NewDeposit(OWNER, "Techcombank", null, DepositType.DEMAND, Money.of(10_000_000, "VND"),
                Percentage.of("0.5"), null, null, OPEN_DATE, null, RolloverPolicy.PRINCIPAL_AND_INTEREST, null, null);
    }
}
