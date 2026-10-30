package com.financemanage.reporting.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port – the read model of reports, implemented by the persistence adapter. */
public interface ReportEntryStore {

    Optional<ReportEntry> find(UUID transactionId);

    void save(ReportEntry entry);

    /** Sums of the live entries counted in reports (not excluded), per type, currency, category and day. */
    List<DailyTotal> dailyTotals(UUID ownerId, LocalDate from, LocalDate to);

    /** Live entries between the dates, oldest first. */
    List<ReportEntry> entries(UUID ownerId, LocalDate from, LocalDate to);

    /** Whether the owner's entries were already loaded from the ledger once. */
    boolean isBuilt(UUID ownerId);

    void markBuilt(UUID ownerId);

    void deleteByOwner(UUID ownerId);

    record DailyTotal(String type, String currency, UUID categoryId, LocalDate date, BigDecimal amount) {
    }
}
