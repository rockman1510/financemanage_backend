package com.financemanage.loan.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan_installment", schema = "loan")
class InstallmentJpaEntity {

    @Id
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id")
    LoanJpaEntity loan;
    @Column(name = "period_no", nullable = false)
    int periodNo;
    @Column(name = "due_date", nullable = false)
    LocalDate dueDate;
    @Column(name = "principal_due", nullable = false)
    BigDecimal principalDue;
    @Column(name = "interest_due", nullable = false)
    BigDecimal interestDue;
    @Column(name = "principal_paid", nullable = false)
    BigDecimal principalPaid;
    @Column(name = "interest_paid", nullable = false)
    BigDecimal interestPaid;
    @Column(nullable = false)
    boolean settled;
}
