package com.financemanage.reporting.infrastructure.web;

import com.financemanage.reporting.application.Dashboard;
import com.financemanage.reporting.application.DashboardCalculator;
import com.financemanage.shared.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardController {

    private final DashboardCalculator calculator;
    private final Clock clock;

    DashboardController(DashboardCalculator calculator, Clock clock) {
        this.calculator = calculator;
        this.clock = clock;
    }

    @GetMapping("/api/v1/dashboard")
    Dashboard dashboard(@AuthenticationPrincipal Jwt jwt) {
        return calculator.compute(CurrentUser.id(jwt), LocalDate.now(clock));
    }
}
