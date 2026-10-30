# Backend

Java 21 · Spring Boot 4.1 · Spring Modulith 2.1 · Spring Security (OAuth2 resource server) · JPA/Hibernate ·
Flyway · PostgreSQL (H2 cho dev/test) · ShedLock · springdoc-openapi.

## Lệnh

```bash
./gradlew build                                              # compile + toàn bộ test
./gradlew :modules:loan:test                                  # test 1 module
./gradlew :app:bootRun --args='--spring.profiles.active=dev'  # chạy với H2, không cần Docker
./gradlew :app:bootRun                                        # chạy với PostgreSQL (DB_URL, DB_USERNAME, DB_PASSWORD)
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health/metrics: `/actuator/health`, `/actuator/prometheus`, cấu trúc module: `/actuator/modulith`
- Tài liệu module (PlantUML/AsciiDoc) được sinh khi chạy test: `app/build/spring-modulith-docs/`

## Cấu hình chính (`app/src/main/resources/application.yml`)

| Thuộc tính | Mặc định | Ý nghĩa |
|---|---|---|
| `JWT_SECRET` (`app.security.jwt.secret`) | – | Khoá ký access token (≥ 32 ký tự). **Bắt buộc đổi ở production** |
| `app.security.jwt.access-token-ttl` / `refresh-token-ttl` | `PT15M` / `P30D` | Thời hạn token |
| `ENCRYPTION_KEY` (`app.security.encryption-key`) | – | Khoá mã hoá secret 2FA trong DB (≥ 32 ký tự). **Bắt buộc đổi ở production**; đổi khoá thì 2FA đã bật không dùng được nữa |
| `GOOGLE_CLIENT_IDS` (`app.identity.google.client-ids`) | trống | OAuth client id (web, Android, iOS) được chấp nhận làm `aud` của Google ID token. Trống = tắt đăng nhập Google |
| `spring.mail.host`, `port`, `username`, `password`, `MAIL_FROM` | – | SMTP gửi email (mã xác thực, đặt lại mật khẩu). Không cấu hình thì email không được gửi (chỉ ghi log). Profile `dev`/`test` dùng hộp thư giả |
| `app.identity.login.max-attempts`, `lock-duration` | `5`, `PT15M` | Khoá tài khoản sau N lần sai mật khẩu |
| `app.identity.verification.code-ttl`, `max-attempts`, `resend-cooldown` | `PT15M`, `5`, `PT1M` | Mã gửi qua email |
| `app.identity.two-factor.issuer`, `challenge-ttl`, `max-attempts` | `FinanceManage`, `PT5M`, `5` | 2FA (TOTP) |
| `app.identity.cleanup-cron` | `0 45 3 * * *` | Job dọn token, mã và challenge hết hạn |
| `app.deposit.maturity-cron`, `app.deposit.reminder-days-before` | `0 5 0 * * *`, `7` | Job đáo hạn/tái tục và nhắc trước N ngày |
| `app.loan.reminder-cron`, `app.loan.reminder-days-before` | `0 15 0 * * *`, `3` | Job nhắc kỳ trả nợ và cảnh báo quá hạn |
| `app.ledger.recurring-cron` | `0 30 0 * * *` | Job ghi giao dịch định kỳ đến hạn |
| `ATTACHMENTS_DIR` (`app.ledger.attachments.directory`), `app.ledger.attachments.max-size` | `./data/attachments`, `5MB` | Nơi lưu và kích thước tối đa của ảnh hoá đơn |
| `app.currency.refresh-cron` | `0 0 6 * * *` | Job lấy tỷ giá từ các `ExchangeRateProvider` (không làm gì khi chưa có provider) |
| `app.reporting.upcoming-days`, `projection-months` | `30`, `12` | Dashboard |
| `spring.cache.type` | `simple` | Đổi sang `redis` (thêm `spring-boot-starter-data-redis`) khi chạy nhiều instance |

## API chính (`/api/v1`)

| Nhóm | Endpoint |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/google`, `/auth/2fa/verify`, `/auth/refresh`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password` |
| Tài khoản | `GET/PATCH/DELETE /me`, `POST /me/password`, `POST /me/email/verification-code`, `POST /me/email/verify`, `GET/DELETE /me/sessions`, `DELETE /me/sessions/{id}`, `POST /me/2fa/setup`, `/me/2fa/enable`, `/me/2fa/disable` |
| Chỉ profile `dev` | `GET /dev/mailbox?to=<email>`: email đã "gửi" (không cần đăng nhập) |
| Ví | `GET/POST /wallets`, `GET /wallets/total?currency=VND`, `GET/PATCH/DELETE /wallets/{id}`, `POST /wallets/{id}/adjust-balance` |
| Danh mục | `GET/POST /categories`, `PATCH/DELETE /categories/{id}` |
| Giao dịch | `GET/POST /transactions` (lọc + phân trang; header `Idempotency-Key`), `GET /transactions/summary`, `GET/PATCH/DELETE /transactions/{id}`, `POST /transactions/{id}/attachments`, `GET/DELETE /attachments/{id}` |
| Chuyển tiền | `POST /transfers` (header `Idempotency-Key`), `DELETE /transfers/{transferId}` |
| Giao dịch định kỳ | `GET/POST /recurring-transactions`, `GET/PATCH/DELETE /recurring-transactions/{id}` |
| Tỷ giá | `GET/POST /exchange-rates`, `DELETE /exchange-rates/{id}`, `GET /exchange-rates/convert` |
| Tiền gửi | `GET/POST /deposits`, `POST /deposits/preview`, `GET/PATCH/DELETE /deposits/{id}`, `GET /deposits/{id}/settlement-quote`, `POST /deposits/{id}/settle` |
| Vay nợ | `GET/POST /loans`, `POST /loans/preview-schedule`, `GET/PATCH/DELETE /loans/{id}`, `POST /loans/{id}/repayments` (header `Idempotency-Key`), `POST /loans/{id}/prepayments`, `POST /loans/{id}/rate-changes` |
| Báo cáo | `GET /dashboard` |
| Thông báo | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read` |

## Mở rộng

- **Cách tính lãi mới**: implement `InterestCalculator`, thêm giá trị vào `InterestCalculationMethod`, khai báo bean.
- **Phương thức trả nợ mới**: kế thừa `MonthlyScheduleGenerator` (hoặc implement `RepaymentScheduleGenerator`).
- **Kênh thông báo mới** (FCM, email, Zalo…): implement `NotificationSender`.
- **Nhà cung cấp email khác** (API HTTP thay cho SMTP): implement `EmailSender`.
- **Nguồn tỷ giá tự động** (bảng tỷ giá ngân hàng…): implement `ExchangeRateProvider`, khai báo bean.
- **Lưu ảnh hoá đơn lên object storage** (S3, R2…): implement `AttachmentStorage` thay cho bản lưu đĩa local.
- **Chuyển sang Keycloak**: thay bean `JwtDecoder` trong `SecurityConfig` bằng `NimbusJwtDecoder.withIssuerLocation(...)`.
- **Tách microservice**: mỗi module đã có schema, migration, API và event riêng → tạo Spring Boot app mới chỉ phụ
  thuộc module đó + `shared-kernel`, chuyển transport của event sang Kafka (`spring-modulith-events-kafka`).
