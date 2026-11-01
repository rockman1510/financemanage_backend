package com.financemanage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financemanage.identity.application.GoogleTokenVerifier;
import com.financemanage.identity.application.GoogleTokenVerifier.GoogleIdentity;
import com.financemanage.identity.domain.Totp;
import com.financemanage.identity.infrastructure.email.DevMailbox;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** End-to-end tests through HTTP, security, JSON mapping, persistence (H2 in PostgreSQL mode) and Flyway. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiIntegrationTest.FakeGoogle.class) // imported explicitly so subclasses (PostgreSQL run) get it too
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DevMailbox mailbox;

    /** Fake Google: "google|subject|email|name" is a valid ID token. */
    @TestConfiguration
    static class FakeGoogle {
        @Bean
        @Primary
        GoogleTokenVerifier fakeGoogleTokenVerifier() {
            return idToken -> {
                String[] parts = idToken.split("\\|");
                if (parts.length != 4 || !parts[0].equals("google")) {
                    throw new BadCredentialsException("Invalid Google ID token");
                }
                return new GoogleIdentity(parts[1], parts[2], parts[3]);
            };
        }
    }

    private String token;
    private final LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void registerUser() throws Exception {
        token = register(UUID.randomUUID() + "@example.com");
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/v1/deposits")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallets")).andExpect(status().isUnauthorized());
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

    @Test
    void walletsCategoriesAndTransactions() throws Exception {
        String categories = mvc.perform(auth(get("/api/v1/categories")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(categories, "$").size()).isGreaterThan(20);
        String food = categoryId(categories, "FOOD");
        String salary = categoryId(categories, "SALARY");

        String cash = createWallet("Tiền mặt", "CASH", "VND", 1_000_000);
        String bank = createWallet("Vietcombank", "BANK_ACCOUNT", "VND", 5_000_000);

        String lunch = "{\"walletId\":\"%s\",\"categoryId\":\"%s\",\"amount\":50000,\"note\":\"Phở bò\"}"
                .formatted(cash, food);
        String first = mvc.perform(auth(json(post("/api/v1/transactions"), lunch)).header("Idempotency-Key", "tx-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.occurredOn").value(today.toString()))
                .andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(auth(json(post("/api/v1/transactions"), lunch)).header("Idempotency-Key", "tx-1"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lunchId = JsonPath.read(first, "$.id");
        assertThat((String) JsonPath.read(replay, "$.id")).isEqualTo(lunchId);
        expectBalance(cash, 950_000);

        mvc.perform(auth(json(post("/api/v1/transactions"), """
                {"walletId":"%s","categoryId":"%s","amount":20000000,"occurredOn":"%s"}"""
                .formatted(bank, salary, today.minusDays(3))))).andExpect(status().isCreated());

        mvc.perform(auth(get("/api/v1/transactions?walletId=" + cash)))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(auth(get("/api/v1/transactions?type=INCOME"))).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(auth(get("/api/v1/transactions?q=phở"))).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(auth(get("/api/v1/transactions?from=" + today.minusDays(1))))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(auth(get("/api/v1/transactions?size=1&page=1")))
                .andExpect(jsonPath("$.items", hasSize(1))).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(auth(get("/api/v1/transactions/summary")))
                .andExpect(jsonPath("$[0].income").value(20_000_000))
                .andExpect(jsonPath("$[0].expense").value(50_000))
                .andExpect(jsonPath("$[0].net").value(19_950_000));

        mvc.perform(auth(json(patch("/api/v1/transactions/" + lunchId),
                        "{\"walletId\":\"%s\",\"amount\":80000,\"note\":\"\"}".formatted(bank))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(bank))
                .andExpect(jsonPath("$.note").doesNotExist());
        expectBalance(cash, 1_000_000);
        expectBalance(bank, 24_920_000);

        mvc.perform(auth(delete("/api/v1/transactions/" + lunchId))).andExpect(status().isNoContent());
        expectBalance(bank, 25_000_000);

        mvc.perform(auth(json(post("/api/v1/wallets/" + cash + "/adjust-balance"), "{\"newBalance\":1200000}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wallet.balance").value(1_200_000))
                .andExpect(jsonPath("$.transaction.type").value("ADJUSTMENT_IN"));

        mvc.perform(auth(delete("/api/v1/wallets/" + bank)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WALLET_HAS_TRANSACTIONS"));
        mvc.perform(auth(json(patch("/api/v1/wallets/" + bank), "{\"archived\":true}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        mvc.perform(auth(get("/api/v1/wallets"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(auth(get("/api/v1/wallets?includeArchived=true"))).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(auth(json(post("/api/v1/transactions"), lunch.replace(cash, bank))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("WALLET_ARCHIVED"));

        String snack = mvc.perform(auth(json(post("/api/v1/categories"),
                        "{\"name\":\"Ăn vặt\",\"type\":\"EXPENSE\",\"parentId\":\"%s\"}".formatted(food))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.parentId").value(food))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(auth(delete("/api/v1/categories/" + food)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_CHILDREN"));
        mvc.perform(auth(delete("/api/v1/categories/" + JsonPath.read(snack, "$.id"))))
                .andExpect(status().isNoContent());

        mvc.perform(auth(json(post("/api/v1/transactions"), "{\"walletId\":\"%s\",\"amount\":-1}".formatted(cash))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.categoryId").exists());
        String stranger = register(UUID.randomUUID() + "@example.com");
        mvc.perform(auth(get("/api/v1/wallets/" + cash), stranger)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/v1/transactions"), stranger)).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void transfersBetweenCurrenciesUseExchangeRates() throws Exception {
        String cash = createWallet("Tiền mặt", "CASH", "VND", 1_000_000);
        String usd = createWallet("Đô la", "CASH", "USD", 0);
        String transfer = "{\"fromWalletId\":\"%s\",\"toWalletId\":\"%s\",\"amount\":500000}".formatted(cash, usd);

        mvc.perform(auth(json(post("/api/v1/transfers"), transfer)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EXCHANGE_RATE_MISSING"));

        String rate = mvc.perform(auth(json(post("/api/v1/exchange-rates"),
                        "{\"base\":\"USD\",\"quote\":\"VND\",\"rate\":25000}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(25000))
                .andExpect(jsonPath("$.effectiveDate").value(today.toString()))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(auth(get("/api/v1/exchange-rates/convert?amount=10&from=USD&to=VND")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.converted").value(250000));
        mvc.perform(auth(get("/api/v1/exchange-rates/convert?amount=10&from=USD&to=XYZ")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));

        String made = mvc.perform(auth(json(post("/api/v1/transfers"), transfer)).header("Idempotency-Key", "tr-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.out.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.in.amount").value(20))
                .andExpect(jsonPath("$.in.currency").value("USD"))
                .andReturn().getResponse().getContentAsString();
        String transferId = JsonPath.read(made, "$.transferId");
        String replay = mvc.perform(auth(json(post("/api/v1/transfers"), transfer)).header("Idempotency-Key", "tr-1"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(replay, "$.transferId")).isEqualTo(transferId);
        expectBalance(cash, 500_000);
        expectBalance(usd, 20);

        mvc.perform(auth(get("/api/v1/wallets/total?currency=VND")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1_000_000))
                .andExpect(jsonPath("$.wallets", hasSize(2)))
                .andExpect(jsonPath("$.missingRates", hasSize(0)));
        mvc.perform(auth(get("/api/v1/transactions/summary"))).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(auth(delete("/api/v1/transfers/" + transferId))).andExpect(status().isNoContent());
        expectBalance(cash, 1_000_000);
        expectBalance(usd, 0);

        mvc.perform(auth(get("/api/v1/exchange-rates?base=USD"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(auth(delete("/api/v1/exchange-rates/" + JsonPath.read(rate, "$.id"))))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/exchange-rates"))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void recurringTransactionsAndAttachments() throws Exception {
        String bank = createWallet("Techcombank", "BANK_ACCOUNT", "VND", 50_000_000);
        String rent = categoryId(mvc.perform(auth(get("/api/v1/categories?type=EXPENSE")))
                .andReturn().getResponse().getContentAsString(), "BILLS_RENT");

        String rule = mvc.perform(auth(json(post("/api/v1/recurring-transactions"), """
                        {"walletId":"%s","categoryId":"%s","amount":5000000,"note":"Tiền nhà","frequency":"MONTHLY",
                         "startDate":"%s"}""".formatted(bank, rent, today.minusMonths(2)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generatedCount").value(3))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String ruleId = JsonPath.read(rule, "$.id");
        String generated = mvc.perform(auth(get("/api/v1/transactions?walletId=" + bank)))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].recurringId").value(ruleId))
                .andReturn().getResponse().getContentAsString();
        expectBalance(bank, 35_000_000);

        mvc.perform(auth(json(patch("/api/v1/recurring-transactions/" + ruleId), "{\"active\":false}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(auth(delete("/api/v1/recurring-transactions/" + ruleId))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/recurring-transactions"))).andExpect(jsonPath("$", hasSize(0)));

        String txId = JsonPath.read(generated, "$.items[0].id");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        String uploaded = mvc.perform(upload(txId, new MockMultipartFile("file", "hoa-don.png", "image/png", png)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("hoa-don.png"))
                .andExpect(jsonPath("$.size").value(4))
                .andReturn().getResponse().getContentAsString();
        String attachmentId = JsonPath.read(uploaded, "$.id");
        mvc.perform(upload(txId, new MockMultipartFile("file", "x.exe", "application/octet-stream", png)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_TYPE_NOT_ALLOWED"));

        mvc.perform(auth(get("/api/v1/transactions/" + txId))).andExpect(jsonPath("$.attachments", hasSize(1)));
        mvc.perform(auth(get("/api/v1/attachments/" + attachmentId)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(content().bytes(png));
        String stranger = register(UUID.randomUUID() + "@example.com");
        mvc.perform(auth(get("/api/v1/attachments/" + attachmentId), stranger)).andExpect(status().isNotFound());

        mvc.perform(auth(delete("/api/v1/attachments/" + attachmentId))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/attachments/" + attachmentId))).andExpect(status().isNotFound());
    }

    @Test
    void emailVerificationPasswordResetAndAccountLock() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String access = register(email);
        mvc.perform(auth(get("/api/v1/me"), access)).andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.hasPassword").value(true));

        mvc.perform(auth(json(post("/api/v1/me/email/verify"), "{\"code\":\"000000x\"}"), access))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
        mvc.perform(auth(json(post("/api/v1/me/email/verify"), "{\"code\":\"%s\"}".formatted(lastCode(email))),
                access)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/me"), access)).andExpect(jsonPath("$.emailVerified").value(true));

        String login = mvc.perform(json(post("/api/v1/auth/login"), credentials(email, "secret123")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(json(post("/api/v1/auth/forgot-password"), "{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/auth/forgot-password"), "{\"email\":\"nobody-%s\"}".formatted(email)))
                .andExpect(status().isNoContent()); // same answer: does not reveal who is registered
        String newPassword = randomPassword();
        String reset = "{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\"}";
        mvc.perform(json(post("/api/v1/auth/reset-password"), reset.formatted(email, "12345", newPassword)))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
        mvc.perform(json(post("/api/v1/auth/reset-password"), reset.formatted(email, lastCode(email), newPassword)))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/auth/refresh"),
                        "{\"refreshToken\":\"%s\"}".formatted((String) JsonPath.read(login, "$.refreshToken"))))
                .andExpect(status().isUnauthorized()); // the reset signed every device out
        mvc.perform(json(post("/api/v1/auth/login"), credentials(email, newPassword))).andExpect(status().isOk());

        for (int i = 0; i < 5; i++) {
            mvc.perform(json(post("/api/v1/auth/login"), credentials(email, "wrong-pass")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(json(post("/api/v1/auth/login"), credentials(email, newPassword)))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void sessionsTwoFactorProfileAndPassword() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String phone = mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email":"%s","password":"secret123","fullName":"An","deviceName":"Điện thoại"}"""
                        .formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String laptop = mvc.perform(json(post("/api/v1/auth/login"),
                        "{\"email\":\"%s\",\"password\":\"secret123\",\"deviceName\":\"Laptop\"}".formatted(email)))
                .andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andReturn().getResponse().getContentAsString();
        String laptopToken = JsonPath.read(laptop, "$.accessToken");

        String sessions = mvc.perform(auth(get("/api/v1/me/sessions"), laptopToken))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.current == true)].deviceName").value("Laptop"))
                .andReturn().getResponse().getContentAsString();
        List<String> phoneSession = JsonPath.read(sessions, "$[?(@.deviceName == 'Điện thoại')].id");
        mvc.perform(auth(delete("/api/v1/me/sessions/" + phoneSession.getFirst()), laptopToken))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/auth/refresh"),
                        "{\"refreshToken\":\"%s\"}".formatted((String) JsonPath.read(phone, "$.refreshToken"))))
                .andExpect(status().isUnauthorized());

        String setup = mvc.perform(auth(post("/api/v1/me/2fa/setup"), laptopToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.otpauthUri").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String secret = JsonPath.read(setup, "$.secret");
        String enabled = mvc.perform(auth(json(post("/api/v1/me/2fa/enable"),
                        "{\"code\":\"%s\"}".formatted(Totp.code(secret, Totp.step(Instant.now())))), laptopToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes", hasSize(10)))
                .andReturn().getResponse().getContentAsString();
        List<String> recovery = JsonPath.read(enabled, "$.recoveryCodes");
        mvc.perform(auth(get("/api/v1/me"), laptopToken)).andExpect(jsonPath("$.twoFactorEnabled").value(true));

        String challenge = mvc.perform(json(post("/api/v1/auth/login"), credentials(email, "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.twoFactorRequired").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String twoFactor = "{\"challengeToken\":\"%s\",\"code\":\"%s\"}";
        String challengeToken = JsonPath.read(challenge, "$.challengeToken");
        mvc.perform(json(post("/api/v1/auth/2fa/verify"), twoFactor.formatted(challengeToken, "000000")))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/auth/2fa/verify"), twoFactor.formatted(challengeToken, recovery.get(0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(auth(json(post("/api/v1/me/2fa/disable"), "{\"code\":\"%s\"}".formatted(recovery.get(1))),
                laptopToken)).andExpect(status().isNoContent());

        mvc.perform(auth(json(patch("/api/v1/me"), """
                        {"fullName":"An Nguyễn","defaultCurrency":"USD","locale":"en","monthStartDay":25}"""),
                        laptopToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultCurrency").value("USD"))
                .andExpect(jsonPath("$.monthStartDay").value(25));
        mvc.perform(auth(json(patch("/api/v1/me"), "{\"timeZone\":\"Mars/Base\"}"), laptopToken))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));

        String changed = randomPassword();
        String change = "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}";
        mvc.perform(auth(json(post("/api/v1/me/password"), change.formatted("wrong", changed)), laptopToken))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        mvc.perform(auth(json(post("/api/v1/me/password"), change.formatted("secret123", changed)), laptopToken))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/me/sessions"), laptopToken)).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(json(post("/api/v1/auth/login"), credentials(email, changed))).andExpect(status().isOk());
    }

    @Test
    void googleSignIn() throws Exception {
        String email = UUID.randomUUID() + "@gmail.com";
        String google = "{\"idToken\":\"google|%s|%s|Bình\"}".formatted(UUID.randomUUID(), email);

        String first = mvc.perform(json(post("/api/v1/auth/google"), google))
                .andExpect(status().isOk()).andExpect(jsonPath("$.twoFactorRequired").value(false))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(json(post("/api/v1/auth/google"), google)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/v1/me"), JsonPath.read(first, "$.accessToken")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.googleLinked").value(true))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(json(post("/api/v1/auth/google"), "{\"idToken\":\"forged\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void deletingTheAccountDeletesItsDataEverywhere() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String access = register(email);
        String food = categoryId(mvc.perform(auth(get("/api/v1/categories"), access))
                .andReturn().getResponse().getContentAsString(), "FOOD");
        String wallet = JsonPath.read(mvc.perform(auth(json(post("/api/v1/wallets"),
                        "{\"name\":\"Ví\",\"type\":\"CASH\",\"initialBalance\":100000}"), access))
                .andReturn().getResponse().getContentAsString(), "$.id");
        String txId = JsonPath.read(mvc.perform(auth(json(post("/api/v1/transactions"),
                "{\"walletId\":\"%s\",\"categoryId\":\"%s\",\"amount\":1000}".formatted(wallet, food)), access))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(multipart("/api/v1/transactions/" + txId + "/attachments")
                        .file(new MockMultipartFile("file", "bill.png", "image/png", new byte[] {1, 2}))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                .andExpect(status().isCreated());
        mvc.perform(auth(json(post("/api/v1/deposits"), """
                {"institutionName":"ACB","type":"DEMAND","principal":1000000,"annualRate":0.5,"openDate":"%s"}"""
                .formatted(today)), access)).andExpect(status().isCreated());
        mvc.perform(auth(json(post("/api/v1/loans"), """
                {"counterpartyName":"Bình","direction":"LEND","principal":500000,"termMonths":1,
                 "startDate":"%s","repaymentMethod":"INTEREST_FREE"}""".formatted(today)), access))
                .andExpect(status().isCreated());
        mvc.perform(auth(json(post("/api/v1/exchange-rates"), "{\"base\":\"USD\",\"quote\":\"VND\",\"rate\":25000}"),
                access)).andExpect(status().isOk());

        mvc.perform(auth(json(delete("/api/v1/me"), "{\"password\":\"secret123\",\"confirmEmail\":\"x@y.z\"}"),
                access)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_MISMATCH"));
        mvc.perform(auth(json(delete("/api/v1/me"),
                        "{\"password\":\"secret123\",\"confirmEmail\":\"%s\"}".formatted(email)), access))
                .andExpect(status().isNoContent());

        mvc.perform(auth(get("/api/v1/me"), access)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/auth/login"), credentials(email, "secret123")))
                .andExpect(status().isUnauthorized());
        // other modules delete their data asynchronously, after the account deletion committed
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            mvc.perform(auth(get("/api/v1/wallets?includeArchived=true"), access)).andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(auth(get("/api/v1/transactions"), access)).andExpect(jsonPath("$.totalElements").value(0));
            mvc.perform(auth(get("/api/v1/deposits"), access)).andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(auth(get("/api/v1/loans"), access)).andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(auth(get("/api/v1/exchange-rates"), access)).andExpect(jsonPath("$", hasSize(0)));
        });
    }

    @Test
    void budgetsFollowSpendingAndBillsArePaidThroughTheLedger() throws Exception {
        String categories = mvc.perform(auth(get("/api/v1/categories"))).andReturn().getResponse()
                .getContentAsString();
        String food = categoryId(categories, "FOOD");
        String coffee = categoryId(categories, "FOOD_COFFEE");
        String rent = categoryId(categories, "BILLS_RENT");
        String salary = categoryId(categories, "SALARY");
        String cash = createWallet("Tiền mặt", "CASH", "VND", 10_000_000);

        String budget = mvc.perform(auth(json(post("/api/v1/budgets"), """
                        {"name":"Ăn uống","amount":1000000,"categoryIds":["%s"],"period":"MONTHLY"}"""
                        .formatted(food))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startDate").value(today.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.current.state").value("ON_TRACK"))
                .andExpect(jsonPath("$.current.remaining").value(1_000_000))
                .andReturn().getResponse().getContentAsString();
        String budgetId = JsonPath.read(budget, "$.id");
        mvc.perform(auth(json(post("/api/v1/budgets"), """
                        {"name":"Lương?","amount":1,"categoryIds":["%s"],"period":"MONTHLY"}""".formatted(salary))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));

        mvc.perform(auth(json(post("/api/v1/transactions"),
                "{\"walletId\":\"%s\",\"categoryId\":\"%s\",\"amount\":850000}".formatted(cash, coffee))))
                .andExpect(status().isCreated());
        mvc.perform(auth(get("/api/v1/budgets/" + budgetId)))
                .andExpect(jsonPath("$.current.spent").value(850_000))
                .andExpect(jsonPath("$.current.percent").value(85))
                .andExpect(jsonPath("$.current.state").value("WARNING"))
                .andExpect(jsonPath("$.current.dailySuggestion").isNumber());
        mvc.perform(auth(get("/api/v1/budgets?date=" + today.minusMonths(1).withDayOfMonth(1))))
                .andExpect(jsonPath("$[0].current.state").value("NOT_STARTED"));

        String bill = mvc.perform(auth(json(post("/api/v1/bills"), """
                        {"name":"Tiền nhà","amount":5000000,"walletId":"%s","categoryId":"%s","frequency":"MONTHLY",
                         "firstDueDate":"%s","remindDaysBefore":3}""".formatted(cash, rent, today))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextDueDate").value(today.toString()))
                .andExpect(jsonPath("$.daysUntilDue").value(0))
                .andReturn().getResponse().getContentAsString();
        String billId = JsonPath.read(bill, "$.id");
        mvc.perform(auth(get("/api/v1/bills/upcoming?days=7"))).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(auth(post("/api/v1/bills/" + billId + "/pay")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.dueDate").value(today.toString()));
        mvc.perform(auth(get("/api/v1/bills/" + billId)))
                .andExpect(jsonPath("$.nextDueDate").value(today.plusMonths(1).toString()));
        // the ledger records the expense right after the payment commits
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            mvc.perform(auth(get("/api/v1/bills/" + billId)))
                    .andExpect(jsonPath("$.payments[0].status").value("RECORDED"))
                    .andExpect(jsonPath("$.payments[0].transactionId").isNotEmpty());
            expectBalance(cash, 10_000_000 - 850_000 - 5_000_000);
        });
        mvc.perform(auth(get("/api/v1/transactions?categoryId=" + rent)))
                .andExpect(jsonPath("$.items[0].source").value("BILL"))
                .andExpect(jsonPath("$.items[0].type").value("EXPENSE"));

        mvc.perform(auth(post("/api/v1/bills/" + billId + "/skip")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SKIPPED"));
        mvc.perform(auth(get("/api/v1/bills/" + billId)))
                .andExpect(jsonPath("$.nextDueDate").value(today.plusMonths(2).toString()))
                .andExpect(jsonPath("$.payments", hasSize(2)));
        mvc.perform(auth(get("/api/v1/bills/" + billId), register(UUID.randomUUID() + "@example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/v1/budgets/" + budgetId))).andExpect(status().isNoContent());
        mvc.perform(auth(delete("/api/v1/bills/" + billId))).andExpect(status().isNoContent());
    }

    private String lastCode(String email) {
        String subject = mailbox.messagesTo(email).getFirst().subject();
        Matcher matcher = Pattern.compile("(\\d{6})").matcher(subject);
        assertThat(matcher.find()).as("code in %s", subject).isTrue();
        return matcher.group(1);
    }

    /** Generated at run time so that no password-like literal sits in the source. */
    private static String randomPassword() {
        return "pw-" + UUID.randomUUID();
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    private String createWallet(String name, String type, String currency, long initialBalance) throws Exception {
        String response = mvc.perform(auth(json(post("/api/v1/wallets"), """
                        {"name":"%s","type":"%s","currency":"%s","initialBalance":%d}"""
                        .formatted(name, type, currency, initialBalance))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(initialBalance))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private MockMultipartHttpServletRequestBuilder upload(String transactionId, MockMultipartFile file) {
        return multipart("/api/v1/transactions/" + transactionId + "/attachments").file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private void expectBalance(String walletId, long balance) throws Exception {
        mvc.perform(auth(get("/api/v1/wallets/" + walletId))).andExpect(jsonPath("$.balance").value(balance));
    }

    private static String categoryId(String categoriesJson, String code) {
        List<String> ids = JsonPath.read(categoriesJson, "$[?(@.code=='" + code + "')].id");
        return ids.getFirst();
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
