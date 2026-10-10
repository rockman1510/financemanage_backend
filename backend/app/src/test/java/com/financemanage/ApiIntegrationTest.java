package com.financemanage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End-to-end tests through HTTP, security, JSON mapping, persistence (H2 in PostgreSQL mode) and Flyway. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private String token;
    private final LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void registerUser() throws Exception {
        token = register(UUID.randomUUID() + "@example.com");
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/v1/deposits")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void authenticationFlow() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);

        mvc.perform(json(post("/api/v1/auth/register"), registerBody(email))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        mvc.perform(json(post("/api/v1/auth/login"), "{\"email\":\"%s\",\"password\":\"wrong-pass\"}".formatted(email)))
                .andExpect(status().isUnauthorized());

        String login = mvc.perform(json(post("/api/v1/auth/login"),
                        "{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email.toUpperCase())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(login, "$.refreshToken");

        mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"%s\"}".formatted(refresh)))
                .andExpect(status().isUnauthorized());

        mvc.perform(auth(get("/api/v1/me"), JsonPath.read(login, "$.accessToken")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void depositLifecycle() throws Exception {
        String body = """
                {"institutionName":"Vietcombank","type":"TERM","principal":100000000,"currency":"VND",
                 "annualRate":6,"earlyWithdrawalRate":0.5,"termMonths":6,"openDate":"%s",
                 "rolloverPolicy":"NONE"}""".formatted(today.minusMonths(3));

        mvc.perform(auth(json(post("/api/v1/deposits/preview"), body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.maturityDate").value(today.plusMonths(3).toString()));

        String created = mvc.perform(auth(json(post("/api/v1/deposits"), body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.transactions", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");
        assertThat(((Number) JsonPath.read(created, "$.accruedInterest")).longValue()).isPositive();

        mvc.perform(auth(get("/api/v1/deposits"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(auth(get("/api/v1/deposits?status=SETTLED"))).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(auth(json(patch("/api/v1/deposits/" + id), "{\"institutionName\":\"BIDV\",\"note\":\"Quỹ\"}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.institutionName").value("BIDV"));

        mvc.perform(auth(get("/api/v1/deposits/" + id + "/settlement-quote")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.early").value(true));

        mvc.perform(auth(post("/api/v1/deposits/" + id + "/settle")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.early").value(true));
        mvc.perform(auth(get("/api/v1/deposits/" + id)))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.transactions", hasSize(2)));
        mvc.perform(auth(post("/api/v1/deposits/" + id + "/settle")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DEPOSIT_ALREADY_SETTLED"));

        mvc.perform(auth(delete("/api/v1/deposits/" + id))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/deposits/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void validatesRequests() throws Exception {
        mvc.perform(auth(json(post("/api/v1/deposits"), "{\"type\":\"TERM\",\"principal\":-5}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.principal").exists())
                .andExpect(jsonPath("$.fieldErrors.institutionName").exists());
    }

    @Test
    void loanLifecycleWithIdempotentRepaymentPrepaymentAndRateChange() throws Exception {
        String body = """
                {"counterpartyName":"VPBank","direction":"BORROW","type":"UNSECURED","principal":120000000,
                 "annualRate":12,"termMonths":12,"startDate":"%s","repaymentMethod":"EQUAL_PRINCIPAL",
                 "earlyRepaymentFeeRate":1}""".formatted(today.minusDays(10));

        mvc.perform(auth(json(post("/api/v1/loans/preview-schedule"), body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installments", hasSize(12)))
                .andExpect(jsonPath("$.totalInterest").value(7800000));

        String created = mvc.perform(auth(json(post("/api/v1/loans"), body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.outstandingPrincipal").value(120000000))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.summary.id");

        String repayment = "{\"amount\":11200000,\"paidOn\":\"%s\"}".formatted(today);
        String first = mvc.perform(auth(json(post("/api/v1/loans/" + id + "/repayments"), repayment))
                        .header("Idempotency-Key", "pay-1"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(auth(json(post("/api/v1/loans/" + id + "/repayments"), repayment))
                        .header("Idempotency-Key", "pay-1"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(replay, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));

        mvc.perform(auth(json(post("/api/v1/loans/" + id + "/rate-changes"),
                        "{\"effectiveFrom\":\"%s\",\"annualRate\":24}".formatted(today.plusMonths(3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePeriods", hasSize(2)))
                .andExpect(jsonPath("$.installments", hasSize(12)));

        mvc.perform(auth(json(post("/api/v1/loans/" + id + "/prepayments"),
                        "{\"principal\":55000000,\"paidOn\":\"%s\"}".formatted(today))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feePortion").value(550000));

        mvc.perform(auth(get("/api/v1/loans/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.outstandingPrincipal").value(55000000))
                .andExpect(jsonPath("$.repayments", hasSize(2)))
                .andExpect(jsonPath("$.installments[0].status").value("PAID"))
                .andExpect(jsonPath("$.installments[1].principalDue").value(5000000));

        mvc.perform(auth(get("/api/v1/loans?direction=BORROW&status=ACTIVE"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(auth(get("/api/v1/loans?direction=LEND"))).andExpect(jsonPath("$", hasSize(0)));

        String stranger = register(UUID.randomUUID() + "@example.com");
        mvc.perform(auth(get("/api/v1/loans/" + id), stranger)).andExpect(status().isNotFound());
    }

    @Test
    void dashboardAggregatesDepositsAndLoans() throws Exception {
        mvc.perform(auth(json(post("/api/v1/deposits"), """
                {"institutionName":"ACB","type":"TERM","principal":50000000,"annualRate":5,"termMonths":1,
                 "openDate":"%s"}""".formatted(today.minusDays(15))))).andExpect(status().isCreated());
        mvc.perform(auth(get("/api/v1/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals[0].deposits").value(50000000))
                .andExpect(jsonPath("$.upcoming", hasSize(1)))
                .andExpect(jsonPath("$.upcoming[0].currency").value("VND"))
                .andExpect(jsonPath("$.upcoming[0].direction").value("IN"))
                .andExpect(jsonPath("$.upcoming[0].amount").isNumber());

        // the cached dashboard is evicted when a loan is added
        mvc.perform(auth(json(post("/api/v1/loans"), """
                {"counterpartyName":"Bình","direction":"BORROW","principal":20000000,"termMonths":1,
                 "startDate":"%s","repaymentMethod":"INTEREST_FREE"}""".formatted(today))))
                .andExpect(status().isCreated());
        String dashboard = mvc.perform(auth(get("/api/v1/dashboard")))
                .andExpect(jsonPath("$.totals[0].borrowedOutstanding").value(20000000))
                .andExpect(jsonPath("$.activeLoans").value(1))
                .andReturn().getResponse().getContentAsString();
        long accrued = ((Number) JsonPath.read(dashboard, "$.totals[0].accruedInterest")).longValue();
        long netWorth = ((Number) JsonPath.read(dashboard, "$.totals[0].netWorth")).longValue();
        assertThat(accrued).isPositive();
        assertThat(netWorth).isEqualTo(50_000_000 + accrued - 20_000_000);

        mvc.perform(auth(get("/api/v1/notifications"))).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/notifications/unread-count"))).andExpect(jsonPath("$.count").isNumber());
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(json(post("/api/v1/auth/register"), registerBody(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private static String registerBody(String email) {
        return "{\"email\":\"%s\",\"password\":\"secret123\",\"fullName\":\"Nguyễn Văn A\"}".formatted(email);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return auth(builder, token);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }
}
