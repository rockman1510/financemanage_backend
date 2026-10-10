package com.financemanage.loan;

import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanType;
import com.financemanage.loan.domain.model.NewLoan;
import com.financemanage.loan.domain.model.RepaymentMethod;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.Percentage;
import java.time.LocalDate;
import java.util.UUID;

public final class LoanFixtures {

    public static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final LocalDate START = LocalDate.of(2025, 1, 15);

    private LoanFixtures() {
    }

    public static NewLoan loan(RepaymentMethod method, long principal, String rate, int months) {
        return loan(method, principal, rate, months, LoanDirection.BORROW);
    }

    public static NewLoan loan(RepaymentMethod method, long principal, String rate, int months,
                               LoanDirection direction) {
        return new NewLoan(OWNER, "VPBank", direction, LoanType.UNSECURED, Money.of(principal, "VND"),
                Percentage.of(rate), months, START, method, Percentage.of("1"), null);
    }
}
