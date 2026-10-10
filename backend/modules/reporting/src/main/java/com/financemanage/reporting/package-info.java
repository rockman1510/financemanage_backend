/**
 * Reporting module: read-side aggregation (dashboard, net worth, cash-flow projection) built only on the public
 * query APIs of the deposit and loan modules. Results are cached and evicted by domain events.
 */
@ApplicationModule(displayName = "Reporting", allowedDependencies = {"shared", "deposit :: api", "loan :: api"})
package com.financemanage.reporting;

import org.springframework.modulith.ApplicationModule;
