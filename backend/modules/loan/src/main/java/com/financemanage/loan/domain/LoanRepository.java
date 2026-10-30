package com.financemanage.loan.domain;

import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface LoanRepository {

    Loan save(Loan loan);

    Optional<Loan> findByIdAndOwner(UUID id, UUID ownerId);

    List<Loan> findByOwner(UUID ownerId, LoanDirection direction, LoanStatus status);

    /** Active loans having an unpaid installment due on {@code date}. */
    List<Loan> findActiveWithUnpaidInstallmentDueOn(LocalDate date);

    void delete(Loan loan);

    /** Removes every loan of the owner (account deletion). */
    void deleteByOwner(UUID ownerId);
}
