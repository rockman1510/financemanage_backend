/**
 * Budget module (Ngân sách): spending limits per category, wallet and period, how much is left and how much can be
 * spent per day, and alerts when a budget reaches its warning level or is exceeded. Spending is read from the ledger
 * through its query API. Only the {@code api} named interface may be used by other modules.
 */
@ApplicationModule(displayName = "Budget")
package com.financemanage.budget;

import org.springframework.modulith.ApplicationModule;
