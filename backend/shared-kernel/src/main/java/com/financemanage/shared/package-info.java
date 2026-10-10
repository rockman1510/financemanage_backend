/**
 * Shared kernel: value objects and cross-cutting web/security helpers used by every module.
 * Declared OPEN so that all of its sub-packages are accessible to other modules.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Shared Kernel")
package com.financemanage.shared;

import org.springframework.modulith.ApplicationModule;
