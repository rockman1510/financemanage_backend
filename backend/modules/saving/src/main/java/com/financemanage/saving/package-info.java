/**
 * Saving module (Tiết kiệm): savings goals with a target and an optional deadline, money moved in and out of a goal
 * from wallets (through ledger commands, like bills), automatic contributions, round-ups of expenses and milestone
 * events. Only the {@code api} named interface may be used by other modules.
 */
@ApplicationModule(displayName = "Saving")
package com.financemanage.saving;

import org.springframework.modulith.ApplicationModule;
