package com.financemanage;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Fails the build when a module reaches into another module's internals (anything outside its "api" named
 * interface) or when module dependencies form a cycle – this is what keeps the monolith splittable.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(FinanceManageApplication.class);

    @Test
    void verifiesModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(modules).writeDocumentation();
    }
}
