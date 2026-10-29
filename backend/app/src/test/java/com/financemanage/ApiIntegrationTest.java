package com.financemanage;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

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
