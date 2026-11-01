package com.financemanage.bill.domain;

import com.financemanage.bill.domain.model.Bill;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – implemented by the persistence adapter. */
public interface BillRepository {

    Bill save(Bill bill);

    Optional<Bill> findByIdAndOwner(UUID id, UUID ownerId);

    Optional<Bill> findByPayment(UUID paymentId);

    List<Bill> findByOwner(UUID ownerId);

    /** Active bills whose next due date is between the two dates (inclusive). */
    List<Bill> findActiveDueBetween(LocalDate from, LocalDate to);

    void delete(Bill bill);

    void deleteByOwner(UUID ownerId);
}
