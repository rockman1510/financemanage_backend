package com.financemanage.deposit.application;

import com.financemanage.deposit.domain.DepositRepository;
import com.financemanage.deposit.domain.model.Deposit;
import com.financemanage.deposit.domain.model.NewDeposit;
import com.financemanage.deposit.domain.model.SettlementQuote;
import com.financemanage.ledger.api.LedgerCommands.RecordExternalTransaction;
import com.financemanage.ledger.api.LedgerQueryApi;
import com.financemanage.ledger.api.WalletBalance;
import com.financemanage.shared.domain.DomainException;
import com.financemanage.shared.domain.Money;
import com.financemanage.shared.domain.NotFoundException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links deposits to wallets: opening can take the principal from a wallet, settling can pay the total into one. The
 * wallet side is a {@link RecordExternalTransaction} command to the ledger (EXTERNAL_OUT / EXTERNAL_IN, never income
 * or expense); its answer marks the link RECORDED or FAILED.
 */
@Service
@Transactional
public class DepositWalletService {

    static final String SOURCE_OPEN = "DEPOSIT_OPEN";
    static final String SOURCE_SETTLE = "DEPOSIT_SETTLE";

    private final DepositService deposits;
    private final DepositRepository repository;
    private final LedgerQueryApi ledger;
    private final ApplicationEventPublisher events;

    public DepositWalletService(DepositService deposits, DepositRepository repository, LedgerQueryApi ledger,
                                ApplicationEventPublisher events) {
        this.deposits = deposits;
        this.repository = repository;
        this.ledger = ledger;
        this.events = events;
    }

    public DepositView openFromWallet(NewDeposit spec, UUID walletId) {
        requireWallet(spec.ownerId(), walletId, spec.principal());
        Deposit deposit = deposits.open(spec).deposit();
        deposit.fundFrom(walletId);
        Deposit saved = repository.save(deposit);
        Money principal = saved.getPrincipal();
        events.publishEvent(new RecordExternalTransaction(saved.getId(), SOURCE_OPEN, saved.getId(),
                saved.getOwnerId(), walletId, false, null, principal.amount(), principal.currencyCode(),
                saved.getOpenDate(), "Gửi tiết kiệm: " + saved.getInstitutionName()));
        return deposits.get(saved.getId(), saved.getOwnerId());
    }

    public SettlementQuote settleToWallet(UUID id, UUID ownerId, LocalDate date, UUID walletId) {
        Deposit deposit = repository.findByIdAndOwner(id, ownerId)
                .orElseThrow(() -> new NotFoundException("Deposit", id));
        requireWallet(ownerId, walletId, deposit.getPrincipal());
        SettlementQuote quote = deposits.settle(id, ownerId, date);
        Deposit settled = repository.findByIdAndOwner(id, ownerId).orElseThrow();
        settled.payTo(walletId);
        repository.save(settled);
        events.publishEvent(new RecordExternalTransaction(id, SOURCE_SETTLE, id, ownerId, walletId, true, null,
                quote.total().amount(), quote.total().currencyCode(), quote.date(),
                "Tất toán tiết kiệm: " + settled.getInstitutionName()));
        return quote;
    }

    /** The ledger answered a command of this module. */
    public void answered(String source, UUID depositId, boolean recorded) {
        repository.findById(depositId).ifPresent(deposit -> {
            if (SOURCE_OPEN.equals(source)) {
                deposit.fundingAnswered(recorded);
            } else {
                deposit.payoutAnswered(recorded);
            }
            repository.save(deposit);
        });
    }

    private void requireWallet(UUID ownerId, UUID walletId, Money amount) {
        WalletBalance wallet = ledger.walletBalances(ownerId).stream().filter(w -> w.walletId().equals(walletId))
                .findFirst().orElseThrow(() -> new NotFoundException("Wallet", walletId));
        if (wallet.archived()) {
            throw new DomainException("WALLET_ARCHIVED", "Wallet " + wallet.name() + " is archived");
        }
        if (!wallet.balance().currency().equals(amount.currency())) {
            throw new DomainException("CURRENCY_MISMATCH", "The wallet holds " + wallet.balance().currencyCode());
        }
    }
}
