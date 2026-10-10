package com.financemanage.loan.infrastructure.persistence;

import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import com.financemanage.loan.domain.model.LoanType;
import com.financemanage.loan.domain.model.RepaymentMethod;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "loan", schema = "loan")
class LoanJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(name = "counterparty_name", nullable = false)
    String counterpartyName;
    @Enumerated(EnumType.STRING)
    LoanDirection direction;
    @Enumerated(EnumType.STRING)
    LoanType type;
    @Column(name = "principal_amount", nullable = false)
    BigDecimal principalAmount;
    @Column(nullable = false, length = 3)
    String currency;
    @Column(name = "term_months", nullable = false)
    int termMonths;
    @Column(name = "start_date", nullable = false)
    LocalDate startDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_method")
    RepaymentMethod repaymentMethod;
    @Column(name = "early_repayment_fee_rate", nullable = false)
    BigDecimal earlyRepaymentFeeRate;
    @Enumerated(EnumType.STRING)
    LoanStatus status;
    String note;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;

    @ElementCollection
    @CollectionTable(name = "loan_rate_period", schema = "loan", joinColumns = @JoinColumn(name = "loan_id"))
    @BatchSize(size = 50)
    List<RatePeriodEmbeddable> ratePeriods = new ArrayList<>();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    List<InstallmentJpaEntity> installments = new ArrayList<>();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    List<RepaymentJpaEntity> repayments = new ArrayList<>();

    @Embeddable
    static class RatePeriodEmbeddable {
        @Column(name = "effective_from", nullable = false)
        LocalDate effectiveFrom;
        @Column(name = "annual_rate", nullable = false)
        BigDecimal annualRate;
    }
}
