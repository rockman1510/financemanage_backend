/**
 * Investment module (Đầu tư): assets (stocks, funds, gold, crypto…), buy/sell trades at average cost, realized and
 * unrealized gains, dividends, prices entered by hand or by a {@code PriceProvider}. Cash moves through wallets via
 * ledger commands. Only the {@code api} named interface may be used by other modules.
 */
@ApplicationModule(displayName = "Investment")
package com.financemanage.investment;

import org.springframework.modulith.ApplicationModule;
