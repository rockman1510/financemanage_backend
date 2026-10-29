/**
 * Ledger module (Sổ thu chi): wallets, categories, income/expense transactions, transfers between wallets,
 * recurring transactions and receipt attachments. Wallet balances and transactions live in one module so they are
 * always consistent. Only the {@code api} named interface may be used by other modules.
 */
@ApplicationModule(displayName = "Ledger")
package com.financemanage.ledger;

import org.springframework.modulith.ApplicationModule;
