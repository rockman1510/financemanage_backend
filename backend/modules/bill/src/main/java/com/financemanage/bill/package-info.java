/**
 * Bill module (Hoá đơn): recurring bills with due dates and reminders. Paying a bill asks the ledger, through a
 * command event, to record the expense; a rejection rolls the bill back (saga). Only the {@code api} named interface
 * may be used by other modules.
 */
@ApplicationModule(displayName = "Bill")
package com.financemanage.bill;

import org.springframework.modulith.ApplicationModule;
