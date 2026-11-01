/**
 * Reporting module: read-side aggregation. Income and expense reports run on the module's own read model, built from
 * the ledger's transaction events (no synchronous calls for that data); the dashboard and net worth combine the
 * public query APIs of the other modules. Results are cached (in memory or Redis) and evicted by domain events.
 */
@ApplicationModule(displayName = "Reporting", allowedDependencies = {"shared", "identity :: api", "deposit :: api",
        "loan :: api", "ledger :: api", "currency :: api", "saving :: api", "investment :: api"})
package com.financemanage.reporting;

import org.springframework.modulith.ApplicationModule;
