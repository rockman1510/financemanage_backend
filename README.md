# Finance Manage – Quản lý Tiền gửi & Vay nợ

Ứng dụng quản lý tài chính cá nhân gồm 2 chức năng chính **Tiền gửi** và **Vay nợ**, xây dựng theo
[kế hoạch kiến trúc](docs/ARCHITECTURE_PLAN.md).

| Thư mục | Nội dung |
|---|---|
| [`backend/`](backend) | Java 21 · Spring Boot 4 · Spring Modulith (modular monolith) · PostgreSQL · Flyway |
| [`mobile/`](mobile) | React Native · Expo SDK 57 · Expo Router · TanStack Query · Zustand · React Hook Form + Zod |
| [`docs/`](docs) | Kế hoạch kiến trúc, [phạm vi tính năng mở rộng](docs/FEATURE_SCOPE.md) |
| `docker-compose.yml` | PostgreSQL + backend để chạy local |
| `.github/workflows/ci.yml` | CI: build/test backend, typecheck/lint/test mobile |

## Chạy nhanh

```bash
# 1. Backend + PostgreSQL bằng Docker
docker compose up --build            # API: http://localhost:8080, Swagger: /swagger-ui.html

#    hoặc không cần Docker (H2 file DB)
cd backend && ./gradlew :app:bootRun --args='--spring.profiles.active=dev'

# 2. Mobile
cd mobile
cp .env.example .env                 # chỉnh EXPO_PUBLIC_API_URL (Android emulator: http://10.0.2.2:8080)
npm install
npx expo start
```

## Test

```bash
cd backend && ./gradlew build        # unit test + kiểm tra ranh giới module + API test (H2 & PostgreSQL/Testcontainers)
cd mobile && npm test && npm run typecheck && npm run lint
```

Kiểm chứng business flow qua API (curl + jq, 170 kiểm tra): xem [docs/API_TEST_GUIDE.md](docs/API_TEST_GUIDE.md) và
`scripts/api-flow-test.sh`.

## Kiến trúc tóm tắt

**Backend – Modular Monolith sẵn sàng tách microservice**

```
backend/
├─ shared-kernel/        Money, Percentage, CashFlowItem, xử lý lỗi, CurrentUser (module OPEN)
├─ modules/
│  ├─ identity/          Đăng ký/đăng nhập, Google, 2FA (TOTP), xác thực email, quên mật khẩu, thiết bị, xoá tài khoản
│  ├─ ledger/            Ví, danh mục, giao dịch thu chi, chuyển tiền, giao dịch định kỳ, ảnh hoá đơn
│  ├─ currency/          Tỷ giá (nhập tay hoặc provider) và quy đổi tiền tệ
│  ├─ deposit/           Tiền gửi: domain thuần Java + Strategy tính lãi, JPA adapter, REST, job đáo hạn
│  ├─ loan/              Vay nợ: Strategy lịch trả nợ, trả nợ idempotent, trả trước hạn, lãi thả nổi, job nhắc nợ
│  ├─ notification/      Lắng nghe domain event → tạo thông báo (port NotificationSender)
│  └─ reporting/         Dashboard / tài sản ròng / dòng tiền (CQRS-lite, cache + evict theo event)
└─ app/                  Spring Boot application, cấu hình, Flyway, integration test
```

- Mỗi module theo **Hexagonal**: `domain` (Java thuần) → `application` (use case) → `infrastructure` (web, persistence, scheduler).
- Module chỉ giao tiếp qua **named interface `api`** (query facade + domain event); `ModularityTests` làm build fail nếu vi phạm.
- Mỗi module sở hữu **schema DB riêng** (`identity`, `ledger`, `currency`, `deposit`, `loan`, `notification`) và migration riêng.
- Event giữa module dùng `@ApplicationModuleListener` + **Event Publication Registry (outbox JDBC)** → đổi sang Kafka khi tách service mà không sửa nghiệp vụ.

**Mobile – tổ chức theo feature**

```
mobile/src/
├─ app/          Routes (Expo Router) – chỉ re-export màn hình
├─ features/     auth · deposit · loan · dashboard · notification · settings (api, hooks, schemas, screens, components)
├─ shared/       api client (refresh token tự động), design system (theme tokens), format, session, feature flags
└─ i18n/         vi / en
```

## Ba yếu tố chất lượng

| Yếu tố | Cách hiện thực |
|---|---|
| Dễ customize | Strategy `InterestCalculator` / `RepaymentScheduleGenerator` (thêm cách tính = thêm 1 bean); cấu hình qua `application.yml` (cron, số ngày nhắc, TTL token…); design tokens trong `theme.ts`; feature flags `EXPO_PUBLIC_FEATURE_*`; i18n |
| Dễ tối ưu | Index theo truy vấn, `@BatchSize`/`default_batch_fetch_size`, lãi tích luỹ tính khi đọc, dashboard cache + evict theo event, virtual threads, Actuator/Prometheus; client cache với TanStack Query |
| Dễ mở rộng | Thêm domain = thêm module; port/adapter cho tích hợp ngoài (`NotificationSender`, `TokenIssuer`); API versioning `/api/v1`; JWT chuẩn (đổi sang Keycloak chỉ thay `JwtDecoder`); outbox sẵn sàng Kafka |
