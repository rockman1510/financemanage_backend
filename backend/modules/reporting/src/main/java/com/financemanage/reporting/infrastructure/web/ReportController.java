package com.financemanage.reporting.infrastructure.web;

import com.financemanage.reporting.application.ReportProjection;
import com.financemanage.reporting.application.ReportService;
import com.financemanage.reporting.application.ReportService.Granularity;
import com.financemanage.reporting.infrastructure.web.ReportDtos.CashFlowResponse;
import com.financemanage.reporting.infrastructure.web.ReportDtos.CategoryReportResponse;
import com.financemanage.reporting.infrastructure.web.ReportDtos.NetWorthResponse;
import com.financemanage.reporting.infrastructure.web.ReportDtos.SummaryResponse;
import com.financemanage.shared.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reports; {@code from}/{@code to} default to the current month, {@code currency} to VND. */
@RestController
@RequestMapping("/api/v1/reports")
class ReportController {

    private final ReportService reports;
    private final ReportProjection projection;

    ReportController(ReportService reports, ReportProjection projection) {
        this.reports = reports;
        this.projection = projection;
    }

    /** Income, expense and net of the period, compared with the period just before. */
    @GetMapping("/summary")
    SummaryResponse summary(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) LocalDate from,
                            @RequestParam(required = false) LocalDate to,
                            @RequestParam(required = false) String currency) {
        return SummaryResponse.from(reports.summary(CurrentUser.id(jwt), from, to, currency));
    }

    /** Expense (default) or income per category, sub-categories grouped under their parent. */
    @GetMapping("/categories")
    CategoryReportResponse categories(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(required = false) String currency) {
        return CategoryReportResponse.from(reports.categories(CurrentUser.id(jwt), from, to, type, currency));
    }

    /** Per month (default) or day: income, expense and money moved to/from savings, investments and deposits. */
    @GetMapping("/cash-flow")
    CashFlowResponse cashFlow(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) LocalDate from,
                              @RequestParam(required = false) LocalDate to,
                              @RequestParam(required = false) Granularity granularity,
                              @RequestParam(required = false) String currency) {
        return CashFlowResponse.from(reports.cashFlow(CurrentUser.id(jwt), from, to, granularity, currency));
    }

    @GetMapping("/net-worth")
    NetWorthResponse netWorth(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String currency) {
        return NetWorthResponse.from(reports.netWorth(CurrentUser.id(jwt), currency));
    }

    @GetMapping(value = "/transactions.csv", produces = "text/csv")
    ResponseEntity<String> csv(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) LocalDate from,
                               @RequestParam(required = false) LocalDate to) {
        String body = reports.csv(CurrentUser.id(jwt), from, to);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"")
                .body(body);
    }

    /** Reloads the read model from the ledger (support tool; reports normally stay in step through events). */
    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rebuild(@AuthenticationPrincipal Jwt jwt) {
        projection.rebuild(CurrentUser.id(jwt));
    }
}
