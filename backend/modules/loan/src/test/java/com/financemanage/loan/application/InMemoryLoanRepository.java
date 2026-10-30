package com.financemanage.loan.application;

import com.financemanage.loan.domain.LoanRepository;
import com.financemanage.loan.domain.model.Loan;
import com.financemanage.loan.domain.model.LoanDirection;
import com.financemanage.loan.domain.model.LoanStatus;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class InMemoryLoanRepository implements LoanRepository {

    final Map<UUID, Loan> store = new LinkedHashMap<>();

    @Override
    public Loan save(Loan loan) {
        store.put(loan.getId(), loan);
        return loan;
    }

    @Override
    public Optional<Loan> findByIdAndOwner(UUID id, UUID ownerId) {
        return Optional.ofNullable(store.get(id)).filter(l -> l.getOwnerId().equals(ownerId));
    }

    @Override
    public List<Loan> findByOwner(UUID ownerId, LoanDirection direction, LoanStatus status) {
        return store.values().stream()
                .filter(l -> l.getOwnerId().equals(ownerId))
                .filter(l -> direction == null || l.getDirection() == direction)
                .filter(l -> status == null || l.getStatus() == status)
                .toList();
    }

    @Override
    public List<Loan> findActiveWithUnpaidInstallmentDueOn(LocalDate date) {
        return store.values().stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .filter(l -> l.getInstallments().stream().anyMatch(i -> !i.isPaid() && i.getDueDate().equals(date)))
                .toList();
    }

    @Override
    public void delete(Loan loan) {
        store.remove(loan.getId());
    }

    @Override
    public void deleteByOwner(UUID ownerId) {
        store.values().removeIf(l -> l.getOwnerId().equals(ownerId));
    }
}
