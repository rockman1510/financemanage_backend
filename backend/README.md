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
| `app.deposit.maturity-cron`, `app.deposit.reminder-days-before` | `0 5 0 * * *`, `7` | Job đáo hạn/tái tục và nhắc trước N ngày |
| `app.loan.reminder-cron`, `app.loan.reminder-days-before` | `0 15 0 * * *`, `3` | Job nhắc kỳ trả nợ và cảnh báo quá hạn |
| `app.reporting.upcoming-days`, `projection-months` | `30`, `12` | Dashboard |
| `spring.cache.type` | `simple` | Đổi sang `redis` (thêm `spring-boot-starter-data-redis`) khi chạy nhiều instance |

## API chính (`/api/v1`)

| Nhóm | Endpoint |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `GET /me` |
| Tiền gửi | `GET/POST /deposits`, `POST /deposits/preview`, `GET/PATCH/DELETE /deposits/{id}`, `GET /deposits/{id}/settlement-quote`, `POST /deposits/{id}/settle` |
| Vay nợ | `GET/POST /loans`, `POST /loans/preview-schedule`, `GET/PATCH/DELETE /loans/{id}`, `POST /loans/{id}/repayments` (header `Idempotency-Key`), `POST /loans/{id}/prepayments`, `POST /loans/{id}/rate-changes` |
| Báo cáo | `GET /dashboard` |
| Thông báo | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read` |

## Mở rộng

- **Cách tính lãi mới**: implement `InterestCalculator`, thêm giá trị vào `InterestCalculationMethod`, khai báo bean.
- **Phương thức trả nợ mới**: kế thừa `MonthlyScheduleGenerator` (hoặc implement `RepaymentScheduleGenerator`).
- **Kênh thông báo mới** (FCM, email, Zalo…): implement `NotificationSender`.
- **Chuyển sang Keycloak**: thay bean `JwtDecoder` trong `SecurityConfig` bằng `NimbusJwtDecoder.withIssuerLocation(...)`.
- **Tách microservice**: mỗi module đã có schema, migration, API và event riêng → tạo Spring Boot app mới chỉ phụ
  thuộc module đó + `shared-kernel`, chuyển transport của event sang Kafka (`spring-modulith-events-kafka`).
