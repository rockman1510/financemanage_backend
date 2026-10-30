# Kế hoạch xây dựng ứng dụng Quản lý Tài chính

> Phạm vi: 2 chức năng chính là **Tiền gửi** (sổ tiết kiệm, tiền gửi có kỳ hạn/không kỳ hạn) và **Vay nợ** (khoản vay ngân hàng, cho vay / đi vay cá nhân).
> Tech stack: **Backend Java (Spring Boot)**, **Frontend React Native**.
> Mục tiêu chất lượng: **dễ customize, dễ tối ưu, dễ mở rộng**.

---

## 1. Có nên dùng Microservice không?

### Kết luận: **Chưa nên tách microservice ngay từ đầu. Bắt đầu bằng _Modular Monolith_, thiết kế sẵn ranh giới để tách thành microservice khi cần.**

| Tiêu chí | Microservice ngay | Modular Monolith trước |
|---|---|---|
| Số domain | 2 domain (Tiền gửi, Vay nợ) – quá ít để bù chi phí phân tán | Đủ để tách module rõ ràng |
| Quy mô đội | Cần nhiều team độc lập | Phù hợp đội nhỏ (1–5 dev) |
| Tính nhất quán dữ liệu tài chính | Phải xử lý Saga, eventual consistency, idempotency phức tạp | Transaction ACID trong 1 DB, đơn giản và an toàn |
| Chi phí vận hành | K8s, service discovery, tracing, nhiều DB, nhiều pipeline | 1 artifact, 1 DB, 1 pipeline |
| Tốc độ ra MVP | Chậm | Nhanh |
| Khả năng tách sau | — | Cao, nếu tuân thủ ranh giới module |

**Khi nào nên tách sang microservice** (tín hiệu rõ ràng):
- Một module có tải khác biệt rõ rệt (VD: job tính lãi hằng ngày cho hàng triệu sổ, hoặc báo cáo nặng) cần scale riêng.
- Có nhiều team, mỗi team sở hữu 1 domain và cần release độc lập.
- Thêm domain mới (Đầu tư, Bảo hiểm, Chi tiêu, Thẻ tín dụng…) khiến codebase lớn.
- Cần công nghệ khác cho một phần (VD: reporting dùng ClickHouse/Elasticsearch).

### Cách làm "Modular Monolith sẵn sàng tách"
Dùng **Spring Modulith** để ép ranh giới module:
- Mỗi module (`deposit`, `loan`, `identity`, `notification`, `reporting`) có package `api` công khai, phần còn lại là `internal`.
- Module **không gọi trực tiếp repository/entity của module khác**, chỉ gọi qua interface `api` hoặc qua **Domain Event**.
- Mỗi module sở hữu **schema DB riêng** (`deposit.*`, `loan.*`) – không JOIN chéo schema.
- Event giữa các module đi qua **Transactional Outbox** (Spring Modulith Event Publication Registry) → sau này đổi transport sang Kafka mà không đổi code nghiệp vụ.
- Viết test `ApplicationModules.verify()` để CI fail khi có vi phạm phụ thuộc.

---

## 2. Kiến trúc mục tiêu (khi tách microservice)

```
                 ┌───────────────────────────┐
  React Native → │  API Gateway              │  Spring Cloud Gateway
                 │  (auth, rate-limit, route)│
                 └──────┬───────────┬────────┘
                        │           │
          ┌─────────────┘           └─────────────┐
          ▼                                       ▼
 ┌─────────────────┐                     ┌─────────────────┐
 │ deposit-service │                     │  loan-service   │
 │  PostgreSQL     │                     │  PostgreSQL     │
 └───────┬─────────┘                     └───────┬─────────┘
         │  outbox → Kafka (domain events)       │
         └──────────────┬────────────────────────┘
                        ▼
   ┌───────────────────┬────────────────────┬───────────────────┐
   │ notification-svc  │ reporting-svc      │ (domain mới...)   │
   │ push/email/SMS    │ read model, dashboard│                 │
   └───────────────────┴────────────────────┴───────────────────┘

 Identity: Keycloak (OAuth2/OIDC)      Config: Spring Cloud Config / K8s ConfigMap
 Observability: OpenTelemetry → Prometheus + Grafana + Loki/Tempo
```

| Service | Trách nhiệm | Ghi chú |
|---|---|---|
| **api-gateway** | Định tuyến, xác thực JWT, rate limiting, CORS | Spring Cloud Gateway |
| **identity** | Đăng ký/đăng nhập, OTP, sinh trắc học (token) | Keycloak, không tự viết |
| **deposit-service** | Sổ tiết kiệm, tính lãi, tất toán, tái tục | |
| **loan-service** | Khoản vay, lịch trả nợ, ghi nhận thanh toán, cho vay cá nhân | |
| **notification-service** | Nhắc đáo hạn, nhắc trả nợ, push (FCM/APNs) | Consumer Kafka |
| **reporting-service** | Tổng tài sản ròng, dòng tiền, biểu đồ | CQRS read model |

**Giao tiếp:** đồng bộ bằng REST (OpenAPI) cho truy vấn; bất đồng bộ bằng Kafka cho sự kiện (`DepositOpened`, `DepositMatured`, `LoanDisbursed`, `RepaymentRecorded`, `RepaymentOverdue`…).
**Nhất quán dữ liệu:** Outbox + idempotent consumer (bảng `processed_event`), Saga choreography cho luồng nhiều bước.
**Resilience:** Resilience4j (timeout, retry, circuit breaker).

---

## 3. Phân tích nghiệp vụ

### 3.1. Tiền gửi (Deposit)
- **Loại:** không kỳ hạn, có kỳ hạn (1, 3, 6, 12… tháng), tích lũy định kỳ.
- **Thuộc tính:** ngân hàng/tổ chức, số tiền gốc, lãi suất (%/năm), kỳ hạn, ngày gửi, ngày đáo hạn, phương thức trả lãi (cuối kỳ / hằng tháng / trả trước), cách tái tục (không / gốc / gốc + lãi), lãi suất rút trước hạn.
- **Chức năng:**
  - CRUD sổ tiết kiệm, đính kèm ảnh sổ.
  - Tính lãi dự kiến, lãi đã tích lũy đến hôm nay, mô phỏng "nếu rút trước hạn".
  - Tất toán, rút một phần, tái tục tự động khi đáo hạn.
  - Nhắc đáo hạn trước N ngày.
  - So sánh lãi suất giữa các ngân hàng (giai đoạn sau).

### 3.2. Vay nợ (Loan)
- **Loại:** vay ngân hàng (thế chấp, tín chấp, trả góp), **đi vay cá nhân**, **cho vay cá nhân**.
- **Thuộc tính:** bên cho vay/bên vay, số tiền gốc, lãi suất (cố định / thả nổi theo giai đoạn), kỳ hạn, phương thức trả (dư nợ giảm dần – gốc đều, annuity – tổng trả đều, trả gốc cuối kỳ, không lãi), phí phạt trả trước/trễ hạn.
- **Chức năng:**
  - Sinh **lịch trả nợ** (amortization schedule).
  - Ghi nhận thanh toán (đủ / một phần / trả trước hạn), tự cập nhật dư nợ & lịch.
  - Cảnh báo quá hạn, nhắc kỳ trả.
  - Thay đổi lãi suất theo giai đoạn (lãi thả nổi) → tính lại lịch.
  - Sổ nợ cá nhân: ai nợ mình, mình nợ ai, lịch sử trả.

### 3.3. Dùng chung
- Dashboard: tổng tiền gửi, tổng dư nợ, **tài sản ròng**, dòng tiền dự kiến theo tháng.
- Đa tiền tệ (VND, USD…), đa ngôn ngữ (vi/en).
- Bảo mật: PIN/sinh trắc học, mã hoá dữ liệu nhạy cảm, audit log.

---

## 4. Backend – Thiết kế chi tiết

### 4.1. Stack
| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ / Framework | Java 21 (LTS, virtual threads), Spring Boot 4.x |
| Module hoá | Spring Modulith |
| Persistence | PostgreSQL, Spring Data JPA (+ jOOQ/QueryDSL cho truy vấn báo cáo) |
| Migration | Flyway |
| Cache | Redis (Spring Cache) |
| Job định kỳ | Spring Scheduler + ShedLock (MVP) → Spring Batch khi dữ liệu lớn |
| Messaging | Spring Modulith events (MVP) → Kafka (khi tách) |
| Security | Spring Security + OAuth2 Resource Server, Keycloak |
| API | REST + springdoc-openapi (contract-first, sinh client cho RN) |
| Mapping / Validation | MapStruct, Jakarta Validation |
| Test | JUnit 5, Testcontainers, ArchUnit/Modulith verify |
| Build | Gradle (multi-module) |
| Observability | Micrometer, OpenTelemetry, Actuator |

### 4.2. Kiến trúc trong mỗi module: Hexagonal (Ports & Adapters)
```
deposit/
 ├─ api/                 # interface + DTO công khai cho module khác
 ├─ domain/              # Entity, Value Object, Domain Service, Domain Event – Java thuần, không phụ thuộc Spring
 │   ├─ model/           # Deposit, InterestRate, Money, Term...
 │   ├─ policy/          # InterestCalculator (Strategy), RolloverPolicy...
 │   └─ event/
 ├─ application/         # Use case (OpenDepositUseCase, SettleDepositUseCase...), port in/out
 └─ infrastructure/
     ├─ web/             # REST controller
     ├─ persistence/     # JPA entity, repository adapter
     ├─ messaging/       # publish/consume event
     └─ scheduler/       # job tính lãi, quét đáo hạn
```
Domain thuần Java → dễ test, dễ thay framework/DB, dễ tách service.

### 4.3. Cấu trúc repository (Gradle multi-module)
```
financemanage_backend/
 ├─ platform/            # BOM, convention plugins
 ├─ shared-kernel/       # Money, Currency, Percentage, DateRange, lỗi chuẩn, base event
 ├─ modules/
 │   ├─ identity/
 │   ├─ deposit/
 │   ├─ loan/
 │   ├─ notification/
 │   └─ reporting/
 ├─ app/                 # Spring Boot application – lắp ráp các module (monolith)
 └─ docs/
```
Khi tách: tạo `deposit-service/` chỉ phụ thuộc `modules/deposit` + `shared-kernel` → gần như không sửa code nghiệp vụ.

### 4.4. Mô hình dữ liệu chính (rút gọn)
```
deposit.deposit_account(id, user_id, institution_id, type, principal, currency,
                        annual_rate, term_months, open_date, maturity_date,
                        interest_payment_method, rollover_policy, status, version, ...)
deposit.deposit_transaction(id, deposit_id, type[OPEN|INTEREST|WITHDRAW|SETTLE|ROLLOVER], amount, occurred_at)
deposit.interest_accrual(id, deposit_id, accrual_date, amount)

loan.loan(id, user_id, counterparty_id, direction[BORROW|LEND], type, principal, currency,
          repayment_method, term_months, start_date, status, version, ...)
loan.loan_rate_period(id, loan_id, from_date, to_date, annual_rate)
loan.repayment_schedule(id, loan_id, period_no, due_date, principal_due, interest_due, status)
loan.repayment(id, loan_id, schedule_id, amount, paid_at, idempotency_key)

common: institution, counterparty, audit_log, outbox_event
```
Nguyên tắc: tiền dùng `NUMERIC(19,4)` + `BigDecimal` (không dùng double), optimistic locking (`version`), soft delete, `created_at/updated_at`, mọi bảng có `user_id` + index.

---

## 5. Đảm bảo 3 yếu tố chất lượng

### 5.1. Dễ customize
- **Strategy pattern cho công thức tài chính:**
  `InterestCalculator` (SIMPLE, COMPOUND_MONTHLY, DAILY_ACTUAL_365…), `RepaymentScheduleGenerator` (EQUAL_PRINCIPAL, ANNUITY, BULLET, INTEREST_FREE).
  Thêm cách tính mới = thêm 1 class `@Component` + 1 giá trị enum/config, không sửa code cũ (Open/Closed).
- **Cấu hình sản phẩm trong DB** thay vì hard-code: kỳ hạn, lãi suất tham chiếu theo ngân hàng, quy tắc rút trước hạn, phí phạt.
- **Feature flags** (Unleash/FF4J hoặc bảng config) để bật/tắt tính năng theo user/phiên bản app.
- **Rule engine nhẹ** (tùy chọn, giai đoạn sau): cảnh báo/nhắc nhở cấu hình được (VD: "nhắc trước 7 ngày nếu số tiền > 100tr").
- **Frontend:** design token + theme, i18n, cấu hình màn hình/menu từ backend (remote config).

### 5.2. Dễ tối ưu
- **Đo trước khi tối ưu:** Micrometer + OpenTelemetry, dashboard p95 latency, slow query log.
- **DB:** index theo `(user_id, status)`, `(maturity_date)`, `(due_date, status)`; partition bảng giao dịch/accrual theo thời gian khi lớn; dùng projection/DTO query thay vì load entity graph.
- **Tính toán:** lãi tích lũy tính "lazy" theo công thức khi đọc thay vì ghi mỗi ngày; chỉ ghi accrual khi cần (chốt kỳ, tất toán). Job batch xử lý theo chunk, có ShedLock chống chạy trùng.
- **Cache:** Redis cho dashboard, danh mục ngân hàng, bảng lãi suất; invalidate qua domain event.
- **CQRS cho báo cáo:** reporting module dựng read model từ event → truy vấn dashboard không ảnh hưởng ghi.
- **API:** phân trang cursor-based, ETag/`If-None-Match`, nén gzip, chỉ trả trường cần thiết.
- **Java 21 virtual threads** cho I/O, GraalVM native image (tùy chọn) để giảm thời gian khởi động khi tách service.

### 5.3. Dễ mở rộng
- **Mở rộng chức năng:** domain mới (Chi tiêu, Đầu tư, Thẻ tín dụng) = thêm module mới, giao tiếp qua event, không sửa module cũ.
- **Mở rộng hạ tầng:** ứng dụng stateless (JWT), scale ngang sau load balancer; DB read replica; tách service theo tín hiệu ở mục 1.
- **API versioning:** `/api/v1/...`, tương thích ngược; client RN sinh từ OpenAPI.
- **Tích hợp ngoài:** port `BankDataProvider`, `RateProvider`, `NotificationSender` → thêm adapter (Open Banking, crawler lãi suất, Zalo/SMS) mà không chạm domain.
- **Đa người dùng/gia đình:** chuẩn bị `owner_id` + `household_id` để sau chia sẻ sổ giữa thành viên.

---

## 6. Frontend – React Native

### 6.1. Stack
| Thành phần | Lựa chọn |
|---|---|
| Nền tảng | Expo (bare/prebuild khi cần native module), TypeScript strict |
| Điều hướng | Expo Router (file-based, xây trên React Navigation) |
| Server state | TanStack Query (cache, retry, offline) |
| Client state | Zustand |
| Form | React Hook Form + Zod |
| API client | Sinh tự động từ OpenAPI (orval / openapi-typescript) |
| UI | Design system riêng trên Tamagui/Restyle hoặc NativeWind, theo design token |
| Biểu đồ | Victory Native / react-native-skia |
| Lưu trữ an toàn | expo-secure-store (token), MMKV (cache) |
| Sinh trắc học | expo-local-authentication |
| Push | expo-notifications (FCM/APNs) |
| i18n | i18next |
| Test | Jest, React Native Testing Library, Maestro (E2E) |
| Release | EAS Build/Update (OTA) |

### 6.2. Cấu trúc theo feature
```
src/
 ├─ app/                 # navigation, providers, theme
 ├─ features/
 │   ├─ deposit/         # screens, components, hooks, api, schemas
 │   ├─ loan/
 │   ├─ dashboard/
 │   └─ auth/
 ├─ shared/
 │   ├─ ui/              # design system (Button, MoneyInput, Card...)
 │   ├─ api/             # client sinh từ OpenAPI, interceptor
 │   ├─ lib/             # format tiền/ngày, money math (dinero.js/big.js)
 │   └─ config/          # feature flags, remote config
 └─ i18n/
```
Mỗi feature độc lập → thêm feature mới không ảnh hưởng feature cũ; có thể lazy-load.

### 6.3. Màn hình chính
- **Dashboard:** tài sản ròng, tổng tiền gửi, tổng nợ, sự kiện sắp tới (đáo hạn, kỳ trả).
- **Tiền gửi:** danh sách sổ, chi tiết sổ (lãi tích lũy, timeline), tạo/sửa sổ, mô phỏng rút trước hạn, tất toán.
- **Vay nợ:** danh sách khoản vay/cho vay, chi tiết + lịch trả nợ, ghi nhận thanh toán, máy tính khoản vay.
- **Cài đặt:** hồ sơ, bảo mật (PIN/vân tay), thông báo, ngôn ngữ, tiền tệ.

---

## 7. Bảo mật & tuân thủ
- OAuth2/OIDC (Keycloak), access token ngắn hạn + refresh token xoay vòng, PKCE trên mobile.
- Kiểm tra quyền sở hữu tài nguyên (`user_id`) ở mọi truy vấn – chống IDOR.
- Mã hoá dữ liệu nhạy cảm at-rest (số sổ, số hợp đồng), TLS everywhere, certificate pinning trên app.
- Idempotency-Key cho API ghi nhận tiền (tránh ghi trùng khi retry).
- Audit log mọi thay đổi số liệu tài chính.
- Rate limiting, OWASP dependency check, secret qua Vault/K8s Secret.

---

## 8. DevOps
- **Local:** Docker Compose (Postgres, Redis, Keycloak, [Kafka khi cần]).
- **CI (GitHub Actions):** build, unit test, Testcontainers integration test, Modulith/ArchUnit verify, SonarQube, OWASP check, build image.
- **CD:** Docker image → MVP chạy trên 1 VM/Cloud Run/ECS; khi tách microservice → Kubernetes + Helm/ArgoCD.
- **Môi trường:** dev → staging → production, Flyway migrate tự động.
- **Mobile:** EAS Build cho iOS/Android, OTA update cho thay đổi JS.

---

## 9. Lộ trình triển khai

| Giai đoạn | Thời gian (ước tính) | Nội dung |
|---|---|---|
| **0. Nền tảng** | 1–2 tuần | Gradle multi-module, Spring Boot + Modulith, Docker Compose, Flyway, CI, Keycloak; khung RN (Expo, navigation, theme, i18n, API client sinh từ OpenAPI) |
| **1. Tiền gửi MVP** | 3 tuần | CRUD sổ, tính lãi (Strategy), tất toán, nhắc đáo hạn; màn hình danh sách/chi tiết/tạo sổ |
| **2. Vay nợ MVP** | 3–4 tuần | Khoản vay ngân hàng + nợ cá nhân, sinh lịch trả nợ (gốc đều/annuity), ghi nhận thanh toán, cảnh báo quá hạn |
| **3. Dashboard & Thông báo** | 2 tuần | Tài sản ròng, dòng tiền, biểu đồ; push notification |
| **4. Hardening** | 2 tuần | Bảo mật (PIN/sinh trắc, pinning), hiệu năng, observability, E2E test, beta release |
| **5. Mở rộng** | liên tục | Lãi thả nổi, tái tục tự động, đa tiền tệ, chia sẻ gia đình, so sánh lãi suất, tách microservice khi có tín hiệu |

---

## 10. Rủi ro & cách giảm thiểu
| Rủi ro | Giảm thiểu |
|---|---|
| Sai số tính tiền | `BigDecimal` + quy tắc làm tròn tập trung trong `Money`; bộ test với dữ liệu mẫu từ ngân hàng thật |
| Mỗi ngân hàng tính lãi khác nhau | Strategy + cấu hình theo tổ chức, cho phép user nhập lãi thực tế để đối chiếu |
| Module dính chặt nhau, khó tách | Spring Modulith verify trong CI, cấm truy cập `internal` |
| Over-engineering sớm | Monolith trước, chỉ thêm Kafka/K8s khi có số liệu chứng minh |
| Mất dữ liệu / ghi trùng | Backup tự động, Outbox, Idempotency-Key, optimistic locking |

---

## 11. Trạng thái triển khai (MVP)

Đã hiện thực giai đoạn 0–3 theo kế hoạch trên – xem [README](../README.md):

- **Backend** (`backend/`): modular monolith gồm các module `identity`, `deposit`, `loan`, `notification`, `reporting`
  cùng `shared-kernel`. Domain viết bằng Java thuần, dùng Strategy cho cách tính lãi và lịch trả nợ. Có outbox cho
  event, ShedLock cho các job, Flyway riêng cho từng module. Test gồm unit test, kiểm tra ranh giới module và API test
  chạy trên cả H2 lẫn PostgreSQL (Testcontainers).
- **Mobile** (`mobile/`): Expo SDK 57 + Expo Router, code tổ chức theo feature. API client tự refresh token, có design
  tokens, feature flags và i18n vi/en. Test component và logic bằng Jest + React Native Testing Library.
- **Hạ tầng**: Dockerfile, docker-compose (PostgreSQL + backend) và GitHub Actions CI.
- **Chưa làm (giai đoạn 5)**: Redis cache phân tán, push notification qua FCM/APNs, Keycloak, đa tiền tệ quy đổi, chia
  sẻ dữ liệu trong gia đình, tách microservice.

- **Giai đoạn 1 của phạm vi mở rộng (xong)**: module `ledger` (ví, danh mục, giao dịch thu chi, chuyển tiền, giao dịch
  định kỳ, ảnh hoá đơn) và `currency` (tỷ giá, quy đổi). `identity` phát event `UserRegistered` để tạo danh mục mặc
  định.

- **Giai đoạn 2 của phạm vi mở rộng (xong)**: `identity` mở rộng (xác thực email, quên/đổi mật khẩu, khoá tài khoản,
  đăng nhập Google, quản lý thiết bị, hồ sơ, 2FA TOTP, xoá tài khoản). Event `UserDeleted` để mọi module tự xoá dữ
  liệu của người dùng.

- **Giai đoạn 3 (xong)**: `budget` (đọc số chi qua `LedgerQueryApi`, cảnh báo theo event giao dịch) và `bill`
  (thanh toán = saga: lệnh `RecordExternalTransaction` qua outbox, sổ thu chi trả lời ghi nhận / từ chối, hoá đơn
  tự bù trừ khi bị từ chối).

- **Giai đoạn 4 (xong)**: `notification` mở rộng: push qua Expo, cài đặt (tắt loại, giờ yên lặng, nhắc ghi chép theo
  múi giờ), thông báo ngân sách/hoá đơn, đánh dấu tất cả đã đọc, chống trùng bằng khoá dedup.

- **Giai đoạn 5 (xong)**: `saving` (mục tiêu, nạp/rút qua lệnh `RecordExternalTransaction` → `EXTERNAL_OUT/IN`,
  trích tự động, làm tròn, mốc 50%/100%) và `deposit` nối với ví khi mở/tất toán.
- **Giai đoạn 6 (xong)**: `investment` (tài sản, mua/bán theo giá vốn bình quân, cổ tức, giá nhập tay qua port
  `PriceProvider`, danh mục quy đổi tiền tệ; tiền đi qua ví bằng `RecordExternalTransaction`, nguồn `INVESTMENT`).

Phạm vi giai đoạn tiếp theo (ví, giao dịch, ngân sách, hoá đơn, tiết kiệm, đầu tư, đa tiền tệ, đăng nhập mở rộng,
thông báo) được chốt trong [FEATURE_SCOPE.md](FEATURE_SCOPE.md). Chia sẻ dữ liệu trong gia đình đã bị loại khỏi phạm vi.
