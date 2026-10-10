/**
 * Identity module: registration, login and token issuance (local JWT). The resource-server side only relies on
 * a standard JWT with the user id in {@code sub}, so this module can be replaced by Keycloak without touching
 * the domain modules.
 */
@ApplicationModule(displayName = "Identity")
package com.financemanage.identity;

import org.springframework.modulith.ApplicationModule;
