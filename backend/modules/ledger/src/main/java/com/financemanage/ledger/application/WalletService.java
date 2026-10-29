package com.financemanage.ledger.application;

import com.financemanage.currency.api.CurrencyConversionApi;
import com.financemanage.ledger.api.LedgerEvents.WalletChanged;
import com.financemanage.ledger.api.LedgerEvents.WalletDeleted;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.ledger.domain.RecurringTransactionRepository;
import com.financemanage.ledger.domain.TransactionRepository;
import com.financemanage.ledger.domain.WalletRepository;
import com.financemanage.ledger.domain.model.Wallet;
import com.financemanage.ledger.domain.model.Wallet.NewWallet;
import com.financemanage.ledger.domain.model.Wallet.WalletChanges;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Wallet use cases. Also serves the module's public {@link LedgerQueryApi}. */
@Service
@Transactional
public class WalletService implements LedgerQueryApi {

    private final WalletRepository wallets;
    private final TransactionRepository transactions;
    private final RecurringTransactionRepository recurring;
    private final CurrencyConversionApi conversion;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WalletService(WalletRepository wallets, TransactionRepository transactions,
                         RecurringTransactionRepository recurring, CurrencyConversionApi conversion,
                         ApplicationEventPublisher events, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.recurring = recurring;
        this.conversion = conversion;
        this.events = events;
        this.clock = clock;
    }

    public Wallet open(NewWallet spec) {
        Wallet wallet = wallets.save(Wallet.open(spec));
        events.publishEvent(new WalletChanged(wallet.getId(), wallet.getOwnerId()));
        return wallet;
    }

    @Transactional(readOnly = true)
    public List<Wallet> list(UUID ownerId, boolean includeArchived) {
        return wallets.findByOwner(ownerId, includeArchived);
    }

    @Transactional(readOnly = true)
    public Wallet get(UUID id, UUID ownerId) {
        return load(id, ownerId);
    }

    public Wallet update(UUID id, UUID ownerId, WalletChanges changes) {
        Wallet wallet = load(id, ownerId);
        wallet.update(changes);
        Wallet saved = wallets.save(wallet);
        events.publishEvent(new WalletChanged(id, ownerId));
        return saved;
    }

    /** Only a wallet without transactions can be deleted; archive it to keep its history instead. */
    public void delete(UUID id, UUID ownerId) {
        Wallet wallet = load(id, ownerId);
        if (transactions.existsByWallet(id)) {
            throw new DomainException("WALLET_HAS_TRANSACTIONS",
                    "Wallet has transactions; archive it instead of deleting it");
        }
        recurring.deleteByWallet(id);
        wallets.delete(wallet);
        events.publishEvent(new WalletDeleted(id, ownerId));
    }

    /** Sum of the wallets that count towards the total, converted into {@code currencyCode}. */
    @Transactional(readOnly = true)
    public WalletTotal total(UUID ownerId, String currencyCode, LocalDate asOf) {
        Currency currency = Money.currency(currencyCode);
        LocalDate date = asOf != null ? asOf : LocalDate.now(clock);
        Money total = Money.zero(currency);
        List<WalletTotal.Line> lines = new ArrayList<>();
        Set<String> missingRates = new TreeSet<>();
        for (Wallet wallet : wallets.findByOwner(ownerId, false)) {
            if (wallet.isExcludedFromTotal()) {
                continue;
            }
            Optional<BigDecimal> rate = conversion.rate(ownerId, wallet.currencyCode(), currencyCode, date);
            Money converted = rate.map(r -> new Money(wallet.getBalance().amount().multiply(r), currency).rounded())
                    .orElse(null);
            if (converted == null) {
                missingRates.add(wallet.currencyCode());
            } else {
                total = total.plus(converted);
            }
            lines.add(new WalletTotal.Line(wallet, converted));
        }
        return new WalletTotal(total, lines, missingRates);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletBalance> walletBalances(UUID ownerId) {
        return wallets.findByOwner(ownerId, true).stream()
                .map(w -> new WalletBalance(w.getId(), w.getName(), w.getType().name(), w.getBalance(),
                        w.isExcludedFromTotal(), w.isArchived()))
                .toList();
    }

    private Wallet load(UUID id, UUID ownerId) {
        return wallets.findByIdAndOwner(id, ownerId).orElseThrow(() -> new NotFoundException("Wallet", id));
    }
}
