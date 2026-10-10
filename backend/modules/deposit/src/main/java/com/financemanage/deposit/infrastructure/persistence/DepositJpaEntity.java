package com.financemanage.deposit.infrastructure.persistence;

import com.financemanage.deposit.domain.model.DepositStatus;
import com.financemanage.deposit.domain.model.DepositType;
import com.financemanage.deposit.domain.model.InterestCalculationMethod;
import com.financemanage.deposit.domain.model.InterestPaymentMethod;
import com.financemanage.deposit.domain.model.RolloverPolicy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deposit_account", schema = "deposit")
class DepositJpaEntity {

    @Id
    UUID id;
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;
    @Column(name = "institution_name", nullable = false)
    String institutionName;
    @Column(name = "account_number")
    String accountNumber;
    @Enumerated(EnumType.STRING)
    DepositType type;
    @Column(name = "principal_amount", nullable = false)
    BigDecimal principalAmount;
    @Column(nullable = false, length = 3)
    String currency;
    @Column(name = "annual_rate", nullable = false)
    BigDecimal annualRate;
    @Column(name = "early_withdrawal_rate", nullable = false)
    BigDecimal earlyWithdrawalRate;
    @Column(name = "term_months")
    Integer termMonths;
    @Column(name = "open_date", nullable = false)
    LocalDate openDate;
    @Column(name = "maturity_date")
    LocalDate maturityDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_payment_method")
    InterestPaymentMethod interestPaymentMethod;
    @Enumerated(EnumType.STRING)
    @Column(name = "rollover_policy")
    RolloverPolicy rolloverPolicy;
    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method")
    InterestCalculationMethod calculationMethod;
    @Enumerated(EnumType.STRING)
    DepositStatus status;
    String note;
    @Column(name = "settled_on")
    LocalDate settledOn;
    @Column(name = "settled_amount")
    BigDecimal settledAmount;
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();
    @Version
    Long version;

    @OneToMany(mappedBy = "deposit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredOn ASC, createdAt ASC")
    List<DepositTransactionJpaEntity> transactions = new ArrayList<>();
}
