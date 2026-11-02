# Hướng dẫn dùng API cho app mobile (KMP – Android & iOS)

Tài liệu này dành cho người (và Claude Code) xây app **Kotlin Multiplatform** gọi backend Finance Manage. Nó nói *cách
dùng* API: luồng xác thực, quy ước, cách xử lý tiền và trạng thái bất đồng bộ, từng tính năng và mã lỗi.

- **Đặc tả máy đọc được**: [`docs/api/openapi.json`](api/openapi.json) (OpenAPI 3, 119 endpoint, sinh từ chính
  backend). Khi backend chạy: `GET /v3/api-docs`, giao diện `GET /swagger-ui.html` (production tắt mặc định).
- **Ví dụ gọi tay theo từng luồng**: [`docs/API_TEST_GUIDE.md`](API_TEST_GUIDE.md); script chạy toàn bộ luồng:
  `scripts/api-flow-test.sh`.
- **Phạm vi tính năng**: [`docs/FEATURE_SCOPE.md`](FEATURE_SCOPE.md).

> Mọi JSON mẫu dưới đây được chụp từ backend thật (profile `dev`). Khi tài liệu và `openapi.json` khác nhau về tên
> trường, tin `openapi.json`; khi khác nhau về quy tắc nghiệp vụ, tin tài liệu này và `API_TEST_GUIDE.md`.

## Mục lục

1. [Môi trường và cách chạy backend](#1-môi-trường-và-cách-chạy-backend)
2. [Quy ước chung](#2-quy-ước-chung)
3. [Lỗi](#3-lỗi)
4. [Xác thực và tài khoản](#4-xác-thực-và-tài-khoản)
5. [Ví, danh mục, giao dịch](#5-ví-danh-mục-giao-dịch)
6. [Đa tiền tệ](#6-đa-tiền-tệ)
7. [Ngân sách](#7-ngân-sách)
8. [Hoá đơn định kỳ](#8-hoá-đơn-định-kỳ)
9. [Mục tiêu tiết kiệm](#9-mục-tiêu-tiết-kiệm)
10. [Sổ tiết kiệm (tiền gửi)](#10-sổ-tiết-kiệm-tiền-gửi)
11. [Vay nợ / cho vay](#11-vay-nợ--cho-vay)
12. [Đầu tư](#12-đầu-tư)
13. [Báo cáo và dashboard](#13-báo-cáo-và-dashboard)
14. [Thông báo và push](#14-thông-báo-và-push)
15. [Gợi ý triển khai phía KMP](#15-gợi-ý-triển-khai-phía-kmp)
16. [Việc backend cần làm thêm cho app KMP](#16-việc-backend-cần-làm-thêm-cho-app-kmp)
17. [Dùng tài liệu này với Claude Code CLI](#17-dùng-tài-liệu-này-với-claude-code-cli)

---

## 1. Môi trường và cách chạy backend

| Môi trường | Base URL | Ghi chú |
|---|---|---|
| Local, không Docker | `http://localhost:8080` | `cd backend && ./gradlew :app:bootRun --args='--spring.profiles.active=dev'` (H2, có hộp thư giả) |
| Local, Docker | `http://localhost:8080` | `docker compose up --build` ở thư mục gốc (PostgreSQL) |
| Android emulator → máy host | `http://10.0.2.2:8080` | iOS simulator dùng được `http://localhost:8080` |
| Thiết bị thật trong LAN | `http://<IP máy>:8080` | Android cần cho phép cleartext cho debug; iOS cần ngoại lệ ATS cho debug |
| Production | `https://<DOMAIN>` | Xem [DEPLOYMENT.md](DEPLOYMENT.md) |

Mọi endpoint nghiệp vụ nằm dưới **`/api/v1`**. Kiểm tra backend sống: `GET /actuator/health` → `{"status":"UP"}`
(chỉ local; production không mở actuator ra ngoài).

**Profile `dev`** có thêm `GET /api/v1/dev/mailbox?to=<email>` (không cần token) trả các email đã "gửi" – dùng để lấy
mã xác thực email, mã đặt lại mật khẩu khi phát triển app. Endpoint này **không tồn tại** ở production.

## 2. Quy ước chung

| Chủ đề | Quy ước |
|---|---|
| Định dạng | JSON UTF-8, `Content-Type: application/json`. Tên trường camelCase. Trường không có giá trị trả `null` (không bị bỏ đi) |
| Xác thực | `Authorization: Bearer <accessToken>` cho mọi endpoint trừ `/auth/*` |
| ID | UUID dạng chuỗi |
| Ngày | `LocalDate` ISO `yyyy-MM-dd` (ví dụ `2026-11-01`), theo **giờ Việt Nam** (`Asia/Ho_Chi_Minh`) khi backend mặc định "hôm nay" |
| Thời điểm | `Instant` ISO-8601 UTC, ví dụ `2026-11-01T10:15:07.978238Z` (số chữ số thập phân thay đổi) |
| Giờ trong ngày | `HH:mm[:ss]` (cài đặt thông báo) |
| **Tiền** | Số JSON (`45000`, `40.00`, `94.67`), là **số thập phân chính xác** – không dùng `Double`/`Float` (xem mục 15). Làm tròn theo số lẻ của tiền tệ: VND 0 chữ số, USD 2 chữ số. Gửi số lẻ hơn tiền tệ cho phép → `422 INVALID_AMOUNT` |
| Tiền tệ | Mã ISO 4217 3 chữ cái viết hoa (`VND`, `USD`). Mặc định `VND` khi bỏ trống |
| Phần trăm | Số, ví dụ `6` = 6%/năm; `percent` trong response là 0–100 (có thể > 100 khi vượt) |
| Phân trang | Chỉ `GET /transactions`: `page` (từ 0), `size` (mặc định 20, tối đa 100) → `{items, page, size, totalElements, totalPages}`. Các danh sách khác trả mảng đầy đủ |
| Cập nhật | `PATCH` là cập nhật **một phần**: trường bỏ trống / `null` giữ nguyên. Muốn xoá giá trị có cờ riêng (ví dụ `clearDeadline: true`) |
| Mã HTTP thành công | `200` đọc/cập nhật, `201` tạo, `202` đã nhận yêu cầu xử lý bất đồng bộ, `204` không có nội dung |
| Idempotency | Header `Idempotency-Key` (chuỗi ≤ 100 ký tự, nên dùng UUID) cho `POST /transactions`, `POST /transfers`, `POST /loans/{id}/repayments`, `POST /loans/{id}/prepayments`. Gửi lại cùng key → trả kết quả lần đầu, **không ghi 2 lần**. Dùng cùng key cho các lần thử lại của *cùng một* thao tác (mất mạng, timeout) |
| Phân quyền | Người dùng chỉ thấy dữ liệu của mình; id của người khác → `404` |

### 2.1 Thao tác bất đồng bộ (trạng thái `PENDING`)

Backend là modular monolith giao tiếp bằng event: khi một tính năng cần **ghi tiền vào ví** (trả hoá đơn, nạp/rút mục
tiêu tiết kiệm, mua/bán tài sản, mở/tất toán sổ tiết kiệm bằng ví), nó trả ngay rồi sổ thu chi ghi sau (thường vài trăm
ms). Response chứa trạng thái:

```
PENDING ──(sổ thu chi ghi được)──▶ RECORDED  (+ transactionId)
        └─(bị từ chối: ví lưu trữ, sai tiền tệ…)──▶ FAILED (+ failureReason)
```

App nên: hiển thị ngay kết quả "đang xử lý", rồi **tải lại đối tượng** (poll 1–2 giây, tối đa ~10 giây, hoặc khi người
dùng quay lại màn hình) cho đến khi hết `PENDING`; sau đó làm mới số dư ví. Trong lúc `PENDING`, sửa chính đối tượng đó
có thể nhận `409 CONCURRENT_MODIFICATION` → thử lại sau.

| Tính năng | Endpoint | Trường trạng thái |
|---|---|---|
| Hoá đơn | `POST /bills/{id}/pay` (202) | `payments[].status`: `PENDING/RECORDED/FAILED/SKIPPED` |
| Mục tiêu tiết kiệm | `POST /saving-goals/{id}/deposits` / `withdrawals` (202) | `movements[].status` |
| Đầu tư | `POST /investments/{id}/buys` / `sells` / `incomes` (201) | `trades[].cashStatus`: `NONE/PENDING/RECORDED/FAILED` |
| Sổ tiết kiệm | `POST /deposits` (`fromWalletId`), `POST /deposits/{id}/settle` (`toWalletId`) | `fundingStatus`, `payoutStatus` |

Báo cáo (`/reports/*`) cũng được cập nhật bất đồng bộ sau mỗi giao dịch (vài trăm ms).

## 3. Lỗi

Mọi lỗi có cùng dạng:

```json
{"code":"VALIDATION_FAILED","message":"Request validation failed",
 "fieldErrors":{"name":"must not be blank","type":"must not be null"},
 "timestamp":"2026-11-01T10:15:07.798400582Z"}
```

| HTTP | Khi nào | `code` |
|---|---|---|
| `400` | Sai kiểu/thiếu trường bắt buộc (Bean Validation); JSON hỏng | `VALIDATION_FAILED` (có `fieldErrors`: tên trường → thông báo) |
| `401` | Thiếu/hết hạn access token (body rỗng); sai mật khẩu, refresh token không hợp lệ, mã 2FA sai | rỗng, hoặc `INVALID_CREDENTIALS` |
| `404` | Không tồn tại hoặc không phải của người dùng | `NOT_FOUND` |
| `409` | Xung đột cập nhật đồng thời | `CONCURRENT_MODIFICATION` → tải lại rồi thử lại |
| `422` | Vi phạm quy tắc nghiệp vụ | Mã cụ thể, xem dưới |
| `429` | Quá nhiều lần thử | `ACCOUNT_LOCKED` (sai mật khẩu 5 lần → khoá 15 phút), `RESEND_TOO_SOON` (gửi lại mã < 1 phút) |

Hiển thị cho người dùng nên dựa vào **`code`** (dịch sang tiếng Việt phía app), `message` là tiếng Anh cho log.
Các mã `422` thường gặp theo nhóm:

| Nhóm | Mã |
|---|---|
| Chung | `INVALID_AMOUNT`, `INVALID_CURRENCY`, `CURRENCY_MISMATCH`, `INVALID_NAME`, `INVALID_DATE_RANGE`, `INVALID_PERIOD`, `PERIOD_TOO_LONG`, `NOTE_TOO_LONG`, `FOREIGN_RESOURCE`, `IDEMPOTENCY_KEY_REUSED`, `INVALID_IDEMPOTENCY_KEY` |
| Tài khoản | `EMAIL_TAKEN`, `WRONG_PASSWORD`, `INVALID_CODE`, `NO_CODE`, `EMAIL_ALREADY_VERIFIED`, `TWO_FACTOR_ALREADY_ENABLED`, `TWO_FACTOR_NOT_ENABLED`, `TWO_FACTOR_NOT_SET_UP`, `CONFIRMATION_MISMATCH`, `GOOGLE_SIGN_IN_DISABLED`, `INVALID_LOCALE`, `INVALID_TIME_ZONE`, `INVALID_MONTH_START_DAY` |
| Ví / giao dịch | `WALLET_ARCHIVED`, `WALLET_HAS_TRANSACTIONS`, `WALLET_REQUIRED`, `INVALID_CREDIT_LIMIT`, `INVALID_DAY_OF_MONTH`, `NOTHING_TO_ADJUST`, `TRANSACTION_NOT_EDITABLE`, `SAME_WALLET`, `TRANSFER_AMOUNT_MISMATCH`, `EXCHANGE_RATE_MISSING`, `SAME_CURRENCY` |
| Danh mục | `CATEGORY_IN_USE`, `CATEGORY_HAS_CHILDREN`, `CATEGORY_TOO_DEEP`, `CATEGORY_TYPE_MISMATCH`, `INVALID_CATEGORY` |
| Ảnh hoá đơn | `ATTACHMENT_TYPE_NOT_ALLOWED`, `ATTACHMENT_TOO_LARGE`, `TOO_MANY_ATTACHMENTS` |
| Ngân sách / hoá đơn | `PERIOD_REQUIRED`, `INVALID_END_DATE`, `BILL_FINISHED`, `INVALID_REMIND_DAYS`, `INVALID_INTERVAL`, `PAYMENT_NOT_FOUND` |
| Tiết kiệm | `INSUFFICIENT_SAVINGS`, `GOAL_NOT_EMPTY`, `ROUND_UP_TAKEN`, `INVALID_ROUND_UP`, `INVALID_AUTO_CONTRIBUTION` |
| Tiền gửi | `DEPOSIT_ALREADY_SETTLED`, `INVALID_SETTLEMENT_DATE`, `INVALID_TERM`, `INVALID_RATE` |
| Vay | `OVERPAYMENT`, `SETTLE_DUE_INSTALLMENTS_FIRST`, `LOAN_NOT_ACTIVE`, `INTEREST_FREE_LOAN`, `INVALID_EFFECTIVE_DATE` |
| Đầu tư | `INSUFFICIENT_QUANTITY`, `FEE_TOO_HIGH`, `STALE_PRICE`, `INVALID_QUANTITY`, `INVALID_PRICE` |
| Thông báo | `INVALID_PUSH_TOKEN`, `INVALID_QUIET_HOURS` |

## 4. Xác thực và tài khoản

### 4.1 Token

- **Access token**: JWT HS256, sống **15 phút** (`expiresInSeconds: 900`). Claims: `sub` (user id), `sid` (session id),
  `email`, `name`. App không cần tự giải mã, chỉ gửi trong header.
- **Refresh token**: chuỗi ngẫu nhiên, sống **30 ngày**, **dùng một lần** (rotation): mỗi lần `POST /auth/refresh`
  trả cặp token mới, token cũ hết hiệu lực. Dùng lại token cũ → `401` và **cả phiên bị thu hồi** (chống đánh cắp) →
  app phải đăng xuất về màn hình đăng nhập.
- Mỗi lần đăng nhập tạo một **phiên** (thiết bị) – xem/thu hồi ở `/me/sessions`. Gửi `deviceName` khi đăng nhập/đăng ký
  để người dùng nhận ra thiết bị.

```
POST /api/v1/auth/register  {"email","password","fullName","deviceName"?}        → 201 TokenPair
POST /api/v1/auth/login     {"email","password","deviceName"?}                   → 200 LoginResponse
POST /api/v1/auth/google    {"idToken","deviceName"?}                            → 200 LoginResponse
POST /api/v1/auth/2fa/verify {"challengeToken","code"}                           → 200 TokenPair
POST /api/v1/auth/refresh   {"refreshToken"}                                     → 200 TokenPair
POST /api/v1/auth/logout    {"refreshToken"}                                     → 204 (thu hồi phiên)
POST /api/v1/auth/forgot-password {"email"}                                      → 204 (luôn 204, không lộ email có tồn tại không)
POST /api/v1/auth/reset-password  {"email","code","newPassword"}                 → 204 (đăng xuất mọi thiết bị)
```

```json
// TokenPair
{"accessToken":"eyJhbGciOiJIUzI1NiJ9…","refreshToken":"4XBcRuJiTY30RjWSKpZBRqlFck33YbYRBtCIe-0KBF4","expiresInSeconds":900}

// LoginResponse khi tài khoản bật 2FA: không có token, phải gọi /auth/2fa/verify trong 5 phút
{"accessToken":null,"refreshToken":null,"expiresInSeconds":null,
 "twoFactorRequired":true,"challengeToken":"…","challengeExpiresInSeconds":300}
```

Quy tắc: mật khẩu 8–72 ký tự; sai mật khẩu 5 lần → `429 ACCOUNT_LOCKED` 15 phút; `/auth/2fa/verify` nhận mã TOTP 6 số
**hoặc** một mã khôi phục (dùng một lần), sai 5 lần thì challenge hết hiệu lực.

**Đăng nhập Google**: app lấy **Google ID token** (Android: Credential Manager / Google Identity; iOS: GoogleSignIn SDK)
rồi gửi lên `/auth/google`. Backend kiểm tra chữ ký và `aud` phải nằm trong `GOOGLE_CLIENT_IDS` (khai báo client id
Android, iOS, web). Email chưa có → tạo tài khoản; email đã có → liên kết. Chưa cấu hình → `422 GOOGLE_SIGN_IN_DISABLED`.

### 4.2 Hồ sơ, email, mật khẩu, 2FA, phiên, xoá tài khoản

| Endpoint | Mô tả |
|---|---|
| `GET /me` | Hồ sơ (mẫu dưới) |
| `PATCH /me` `{fullName?, defaultCurrency?, locale?, timeZone?, monthStartDay?}` | `monthStartDay` 1–28 (ngày bắt đầu "tháng" tài chính); `timeZone` IANA (dùng cho nhắc ghi chép) |
| `POST /me/email/verification-code` → 204 | Gửi mã 6 số (hết hạn 15 phút, gửi lại sau ≥ 1 phút, sai 5 lần thì mã hỏng) |
| `POST /me/email/verify` `{code}` → 204 | Xác thực email |
| `POST /me/password` `{currentPassword?, newPassword}` → 204 | `currentPassword` bắt buộc nếu `hasPassword=true`; tài khoản chỉ có Google đặt được mật khẩu lần đầu |
| `POST /me/2fa/setup` → `{secret, otpauthUri}` | Hiển thị QR từ `otpauthUri` cho app Authenticator |
| `POST /me/2fa/enable` `{code}` → `{recoveryCodes:[…10 mã]}` | Hiện mã khôi phục **một lần duy nhất** |
| `POST /me/2fa/disable` `{code}` → 204 | Mã TOTP hoặc mã khôi phục |
| `GET /me/sessions` | `[{id, deviceName, lastActiveAt, expiresAt, current}]` |
| `DELETE /me/sessions/{sessionId}` / `DELETE /me/sessions` | Thu hồi một phiên / mọi phiên khác phiên hiện tại |
| `DELETE /me` `{password?, confirmEmail}` → 204 | Xoá tài khoản và **toàn bộ dữ liệu** mọi module (bất đồng bộ); `confirmEmail` phải đúng email |

```json
// GET /me
{"id":"b068e29e-15ad-441a-bd70-a64e74d262a0","email":"doc9074@example.com","fullName":"Nguyễn Văn A",
 "emailVerified":false,"hasPassword":true,"googleLinked":false,"twoFactorEnabled":false,
 "defaultCurrency":"VND","locale":"vi","timeZone":"Asia/Ho_Chi_Minh","monthStartDay":1,
 "createdAt":"2026-11-01T10:15:07.400614Z"}
```

## 5. Ví, danh mục, giao dịch

### 5.1 Ví

| Endpoint | Mô tả |
|---|---|
| `GET /wallets?includeArchived=false` | Danh sách ví |
| `POST /wallets` | `{name*, type*, currency?, initialBalance?, icon?, excludedFromTotal?, creditLimit?, statementDay?, paymentDueDay?}` |
| `GET/PATCH/DELETE /wallets/{id}` | `PATCH` đổi tên, icon, lưu trữ (`archived`), số dư ban đầu, thông tin thẻ tín dụng. **Không đổi được tiền tệ**. Xoá chỉ khi chưa có giao dịch (`WALLET_HAS_TRANSACTIONS`) – nếu có thì lưu trữ |
| `POST /wallets/{id}/adjust-balance` `{newBalance*, occurredOn?, note?}` | Tạo giao dịch điều chỉnh `ADJUSTMENT_IN/OUT` để số dư thành `newBalance` (không tính vào thu chi) |
| `GET /wallets/total?currency=VND&date=` | Tổng các ví (bỏ ví `excludedFromTotal`), quy đổi một tiền tệ; ví thiếu tỷ giá nằm trong `missingRates` |

`type`: `CASH`, `BANK_ACCOUNT`, `E_WALLET`, `CREDIT_CARD` (có `creditLimit`, `availableCredit`, `statementDay`,
`paymentDueDay` 1–31), `OTHER`. Số dư **có thể âm**. Ví lưu trữ không nhận giao dịch mới (`WALLET_ARCHIVED`).

```json
{"id":"1e1a4d7a-b041-43d7-bea8-542d2b4eaeee","name":"Tiền mặt","type":"CASH","currency":"VND","initialBalance":5000000,
 "balance":5000000,"excludedFromTotal":false,"archived":false,"icon":null,"creditLimit":null,"availableCredit":null,
 "statementDay":null,"paymentDueDay":null}
```

### 5.2 Danh mục

| Endpoint | Mô tả |
|---|---|
| `GET /categories?type=INCOME\|EXPENSE` | Cha đứng trước con. Người dùng mới có sẵn bộ danh mục mặc định |
| `POST /categories` `{name*, type*, parentId?, icon?}` | Tối đa 2 cấp (cha – con); con cùng loại với cha |
| `PATCH /categories/{id}` `{name?, icon?}` | |
| `DELETE /categories/{id}` | Không xoá được khi đang dùng (`CATEGORY_IN_USE`) hoặc còn con (`CATEGORY_HAS_CHILDREN`) |

Danh mục mặc định có `code` cố định (danh mục tự tạo có `code: null`) – dùng `code` để gán icon/màu/bản dịch phía app:
`FOOD` (`FOOD_RESTAURANT`, `FOOD_COFFEE`), `BILLS` (`BILLS_ELECTRICITY`, `BILLS_WATER`, `BILLS_INTERNET`, `BILLS_PHONE`,
`BILLS_RENT`), `TRANSPORT` (`TRANSPORT_FUEL`, `TRANSPORT_PARKING`, `TRANSPORT_TAXI`), `SHOPPING` (`SHOPPING_CLOTHES`,
`SHOPPING_HOUSEHOLD`), `ENTERTAINMENT`, `HEALTH`, `EDUCATION`, `FAMILY`, `FRIENDS`, `GIFTS_DONATIONS`, `INSURANCE`,
`FEES`, `OTHER_EXPENSE`; thu: `SALARY`, `BONUS`, `INTEREST`, `SELLING`, `GIFTS_RECEIVED`, `OTHER_INCOME`.

```json
{"id":"da49d9f1-e2b8-4c5e-956a-ac522e5fde3f","name":"Ăn uống","type":"EXPENSE","parentId":null,"icon":"food","code":"FOOD"}
```

### 5.3 Giao dịch thu chi

| Endpoint | Mô tả |
|---|---|
| `POST /transactions` (+ `Idempotency-Key`) | `{walletId*, categoryId*, amount*, occurredOn?, note?, withPerson?, excludeFromReport?}` – loại (thu/chi) suy ra từ danh mục; `amount` > 0; `occurredOn` mặc định hôm nay; `note` ≤ 500, `withPerson` ≤ 120 ký tự |
| `GET /transactions?walletId&categoryId&type&from&to&minAmount&maxAmount&q&page&size` | Mới nhất trước. `categoryId`, `type` lặp được (`?type=INCOME&type=EXPENSE`); lọc danh mục cha gồm cả con; `q` tìm trong ghi chú/người liên quan |
| `GET /transactions/summary?…` (cùng bộ lọc, trừ phân trang) | `[{currency, income, expense, net}]` mỗi tiền tệ một dòng – chuyển tiền, điều chỉnh, `EXTERNAL_*` và giao dịch `excludeFromReport` **không** tính |
| `GET/PATCH/DELETE /transactions/{id}` | Chỉ sửa/xoá được thu, chi do người dùng ghi (`TRANSACTION_NOT_EDITABLE` với chuyển tiền, điều chỉnh, giao dịch do module khác tạo). Sửa/xoá cập nhật số dư ví |

`type` của giao dịch: `INCOME`, `EXPENSE`, `TRANSFER_IN/OUT` (chuyển giữa ví), `ADJUSTMENT_IN/OUT` (điều chỉnh số dư),
`EXTERNAL_IN/OUT` (tiền đi/về mục tiêu tiết kiệm, đầu tư, sổ tiết kiệm – **không phải thu/chi**). Giao dịch do module
khác tạo có `source` (`BILL`, `SAVING`, `INVESTMENT`, `DEPOSIT_OPEN`, `DEPOSIT_SETTLE`) và `sourceId`.

```json
{"id":"90ddef81-3da6-4529-b7c5-9858e6842e04","walletId":"1e1a4d7a-…","type":"EXPENSE","amount":45000,"currency":"VND",
 "categoryId":"da49d9f1-…","occurredOn":"2026-11-01","note":"Phở","withPerson":null,"excludeFromReport":false,
 "transferId":null,"counterpartWalletId":null,"exchangeRate":null,"recurringId":null,"source":null,"sourceId":null,
 "createdAt":"2026-11-01T10:15:07.978238165Z","attachments":null}
```

### 5.4 Chuyển tiền giữa ví

`POST /transfers` (+ `Idempotency-Key`) `{fromWalletId*, toWalletId*, amount*, receivedAmount?, occurredOn?, note?}` →
`201 {transferId, out, in}` (2 giao dịch `TRANSFER_OUT` / `TRANSFER_IN`). Khác tiền tệ: nhập `receivedAmount` (số tiền
ví nhận thực tế) **hoặc** bỏ trống để dùng tỷ giá của người dùng (mục 6); thiếu tỷ giá → `EXCHANGE_RATE_MISSING`.
`DELETE /transfers/{transferId}` xoá cả hai chiều.

### 5.5 Giao dịch định kỳ

`POST /recurring-transactions` `{walletId*, categoryId*, amount*, note?, withPerson?, frequency*, interval?, startDate*,
endDate?}` – `frequency`: `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`; `interval` mặc định 1 (2 = mỗi 2 tháng…). Job hằng ngày
tự ghi các kỳ tới hạn (không trôi ngày: 31/1 → 28/2 → 31/3). Response có `nextOccurrence`, `generatedCount`, `active`.
`PATCH` đổi ví, danh mục, số tiền, ghi chú, `endDate`, tạm dừng (`active:false`). `GET`, `GET /{id}`, `DELETE /{id}`.

### 5.6 Ảnh hoá đơn

- `POST /transactions/{transactionId}/attachments` – **multipart/form-data**, phần tên **`file`**; JPEG, PNG, WebP,
  HEIC hoặc PDF; ≤ 5 MB; tối đa 5 file mỗi giao dịch → `201 {id, fileName, contentType, size, createdAt}`.
- `GET /attachments/{id}` – tải nội dung (body nhị phân, `Content-Type` gốc; cần header `Authorization`, nên tải bằng
  Ktor rồi hiển thị, không đưa URL thẳng cho thư viện ảnh nếu nó không gửi header).
- `DELETE /attachments/{id}`.

## 6. Đa tiền tệ

| Endpoint | Mô tả |
|---|---|
| `GET /exchange-rates?base&quote` | Tỷ giá người dùng tự nhập + tỷ giá chung (`global:true`, nếu có nguồn tự động) |
| `POST /exchange-rates` `{base*, quote*, rate*, effectiveDate?}` | 1 `base` = `rate` `quote`, ví dụ `USD`→`VND` 25000. Cùng cặp + ngày thì ghi đè |
| `DELETE /exchange-rates/{id}` | |
| `GET /exchange-rates/convert?amount&from&to&date` | `{amount, from, to, rate, converted, date}` |

Quy tắc chọn tỷ giá: tỷ giá của người dùng ưu tiên hơn tỷ giá chung; lấy tỷ giá hiệu lực gần nhất **trước hoặc bằng**
ngày cần; không có cặp thuận thì dùng nghịch đảo của cặp ngược. Báo cáo, tổng ví, ngân sách, tài sản ròng đều quy đổi
theo quy tắc này.

## 7. Ngân sách

| Endpoint | Mô tả |
|---|---|
| `POST /budgets` | `{name*, amount*, currency?, categoryIds?, walletId?, period*, startDate?, endDate?, repeat?}` |
| `GET /budgets?date=` / `GET /budgets/{id}?date=` | Kèm `current`: tình trạng của kỳ chứa `date` (mặc định hôm nay) |
| `PATCH /budgets/{id}` | `{name?, amount?, categoryIds?, walletId?, allWallets?, repeat?}` – `allWallets:true` bỏ giới hạn ví |
| `DELETE /budgets/{id}` | |

- `period`: `WEEKLY` (bắt đầu thứ Hai), `MONTHLY`, `QUARTERLY`, `YEARLY`, `CUSTOM` (cần `startDate`, `endDate`).
  `repeat:true` lặp lại theo kỳ. `categoryIds` rỗng = mọi khoản chi; danh mục cha gồm cả con. Chi tiêu khác tiền tệ được
  quy đổi; tiền tệ thiếu tỷ giá nằm trong `unconvertedCurrencies`.
- `state`: `NOT_STARTED`, `ON_TRACK`, `WARNING` (≥ 80%), `EXCEEDED` (≥ 100%), `ENDED`. `dailySuggestion` = còn lại /
  số ngày còn lại. Đạt 80% và 100% sinh thông báo `BUDGET_WARNING` / `BUDGET_EXCEEDED` (một lần mỗi mức mỗi kỳ).

```json
{"id":"af3b468a-…","name":"Ăn uống tháng","amount":3000000,"currency":"VND","categoryIds":["da49d9f1-…"],"walletId":null,
 "period":"MONTHLY","startDate":"2026-11-01","endDate":null,"repeat":true,
 "current":{"periodStart":"2026-11-01","periodEnd":"2026-11-30","spent":45000,"remaining":2955000,"percent":1,
            "dailySuggestion":98500,"daysLeft":30,"state":"ON_TRACK","unconvertedCurrencies":[]}}
```

## 8. Hoá đơn định kỳ

| Endpoint | Mô tả |
|---|---|
| `POST /bills` | `{name*, amount*, walletId*, categoryId*, frequency*, interval?, firstDueDate*, endDate?, remindDaysBefore?, note?}` – `frequency`: `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`; `categoryId` là danh mục **chi**; tiền tệ theo ví |
| `GET /bills`, `GET /bills/{id}` | Có `nextDueDate`, `daysUntilDue`, `overdue`, `active`, `payments` (mới nhất trước) |
| `GET /bills/upcoming?days=30` | Hoá đơn tới hạn trong N ngày (kể cả quá hạn) |
| `PATCH /bills/{id}` | `{name?, amount?, walletId?, categoryId?, endDate?, remindDaysBefore?, note?, active?}` (`active:false` = tạm dừng) |
| `POST /bills/{id}/pay` `{amount?, walletId?, paidOn?}` | **202**, trả kỳ đang tới hạn: tạo khoản chi qua sổ thu chi (mục 2.1). Mặc định số tiền và ví của hoá đơn. `FAILED` → kỳ đó tới hạn lại |
| `POST /bills/{id}/skip` | Bỏ qua kỳ hiện tại (`SKIPPED`), chuyển sang kỳ sau |
| `DELETE /bills/{id}` | |

Ngày tới hạn không trôi (31/1 → 28/2 → 31/3). Job hằng ngày nhắc `BILL_DUE_SOON` (trước `remindDaysBefore` ngày) và
`BILL_OVERDUE`; trả thất bại sinh `BILL_PAYMENT_FAILED`.

```json
// POST /bills/{id}/pay → 202
{"id":"543950b0-…","dueDate":"2026-11-01","amount":800000,"walletId":"1e1a4d7a-…","paidOn":"2026-11-01",
 "status":"PENDING","transactionId":null,"failureReason":null}
// GET /bills/{id} sau đó: nextDueDate chuyển sang kỳ sau, payments[0].status = RECORDED, có transactionId
```

## 9. Mục tiêu tiết kiệm

| Endpoint | Mô tả |
|---|---|
| `POST /saving-goals` `{name*, target*, currency?, deadline?, icon?}` | |
| `GET /saving-goals`, `GET /saving-goals/{id}` | Có tiến độ và lịch sử `movements` |
| `PATCH /saving-goals/{id}` `{name?, target?, deadline?, clearDeadline?, icon?}` | |
| `DELETE /saving-goals/{id}` | Chỉ khi đã rút hết (`GOAL_NOT_EMPTY`) |
| `POST /saving-goals/{id}/deposits` `{amount*, walletId*, date?}` | **202** – chuyển tiền từ ví vào mục tiêu (ví ghi `EXTERNAL_OUT`, không tính là chi) |
| `POST /saving-goals/{id}/withdrawals` `{amount*, walletId*, date?}` | **202** – rút về ví; không quá số đã tiết kiệm (`INSUFFICIENT_SAVINGS`) |
| `PUT /saving-goals/{id}/auto-contribution` `{amount*, walletId*, frequency*, startDate?}` | Trích tự động `WEEKLY`/`MONTHLY`; ngày bắt đầu hôm nay/quá khứ → trích ngay các kỳ đã tới. `DELETE` để tắt |
| `PUT /saving-goals/{id}/round-up` `{walletId*, unit*}` | Mỗi khoản chi của ví làm tròn lên bội số `unit`, phần chênh lệch vào mục tiêu (47.000, unit 10.000 → để dành 3.000). Mỗi ví một mục tiêu (`ROUND_UP_TAKEN`). `DELETE` để tắt |

Ví phải cùng tiền tệ với mục tiêu. `monthlySuggestion` = còn thiếu / số tháng tới hạn. Đạt 50% và 100% sinh thông báo
`SAVING_GOAL_MILESTONE`. `movements[].source`: `MANUAL`, `AUTO`, `ROUND_UP`.

```json
{"id":"3e1cfb63-…","name":"Du lịch","target":10000000,"currency":"VND","deadline":"2027-04-30","icon":null,
 "saved":1000000,"percent":10,"remaining":9000000,"monthlySuggestion":1500000,"daysLeft":180,"reached":false,
 "autoContribution":null,"roundUp":null,
 "movements":[{"id":"0664c660-…","type":"DEPOSIT","source":"MANUAL","amount":1000000,"walletId":"1e1a4d7a-…",
               "date":"2026-11-01","status":"RECORDED","transactionId":"5e717be3-…","failureReason":null}]}
```

## 10. Sổ tiết kiệm (tiền gửi)

| Endpoint | Mô tả |
|---|---|
| `POST /deposits/preview` (cùng body với tạo) | Xem trước, không lưu: `{maturityDate, expectedInterest, totalAtMaturity, currency}` |
| `POST /deposits` | `{institutionName*, accountNumber?, type*, principal*, currency?, annualRate*, earlyWithdrawalRate?, termMonths?, openDate*, interestPaymentMethod?, rolloverPolicy?, calculationMethod?, note?, fromWalletId?}` |
| `GET /deposits?status=ACTIVE\|MATURED\|SETTLED`, `GET /deposits/{id}` | Kèm `accruedInterest` (lãi tích luỹ tới hôm nay), `expectedInterestAtMaturity`, `transactions` (lịch sử sổ) |
| `PATCH /deposits/{id}` `{institutionName?, accountNumber?, note?}` | |
| `GET /deposits/{id}/settlement-quote?date=` | Báo giá tất toán: `{principal, interest, total, early, interestLostByEarlyWithdrawal}` |
| `POST /deposits/{id}/settle` `{date?, toWalletId?}` | Tất toán; rút trước hạn tính lãi theo `earlyWithdrawalRate` |
| `DELETE /deposits/{id}` | |

- `type`: `DEMAND` (không kỳ hạn) hoặc `TERM` (cần `termMonths`). `interestPaymentMethod`: `AT_MATURITY`, `MONTHLY`,
  `UPFRONT`. `rolloverPolicy` (job đáo hạn tự xử lý): `NONE`, `PRINCIPAL_ONLY`, `PRINCIPAL_AND_INTEREST`.
  `calculationMethod`: `SIMPLE_ACTUAL_365`, `SIMPLE_30_360`, `COMPOUND_MONTHLY`.
- `fromWalletId` (khi mở) trừ tiền gốc khỏi ví; `toWalletId` (khi tất toán) cộng tổng tiền vào ví – trạng thái phía ví ở
  `fundingStatus` / `payoutStatus` (mục 2.1). Không có ví thì sổ chỉ được theo dõi.
- Thông báo: `DEPOSIT_MATURING_SOON` (trước 7 ngày), `DEPOSIT_MATURED`.

```json
// POST /deposits/preview  {"institutionName":"VCB","type":"TERM","principal":100000000,"annualRate":6,"termMonths":6,"openDate":"2026-11-01"}
{"maturityDate":"2027-05-01","expectedInterest":2975342,"totalAtMaturity":102975342,"currency":"VND"}
```

## 11. Vay nợ / cho vay

| Endpoint | Mô tả |
|---|---|
| `POST /loans/preview-schedule` (cùng body với tạo) | Lịch trả nợ xem trước: `{installments, totalInterest, totalPayment, currency}` |
| `POST /loans` | `{counterpartyName*, direction*, type?, principal*, currency?, annualRate?, termMonths*, startDate*, repaymentMethod*, earlyRepaymentFeeRate?, note?}` → `LoanDetailResponse` |
| `GET /loans?direction=BORROW\|LEND&status=ACTIVE\|PAID_OFF` | Danh sách `LoanSummaryResponse` (có `outstandingPrincipal`, `nextDueDate`, `nextDueAmount`, `overdueInstallments`) |
| `GET /loans/{id}` | `{summary, earlyRepaymentFeeRate, totalScheduledInterest, ratePeriods, installments, repayments}` |
| `PATCH /loans/{id}` `{counterpartyName?, note?}`, `DELETE /loans/{id}` | |
| `POST /loans/{id}/repayments` (+ `Idempotency-Key`) `{amount*, paidOn?}` | Trả theo lịch: trừ lãi trước gốc, kỳ cũ trước. Không vượt số nợ (`OVERPAYMENT`). Hết nợ → `PAID_OFF` |
| `POST /loans/{id}/prepayments` (+ `Idempotency-Key`) `{principal*, paidOn?}` | Trả trước gốc, thu phí `earlyRepaymentFeeRate`, tính lại lịch (giữ số kỳ). Phải trả hết kỳ quá hạn/trả dở trước (`SETTLE_DUE_INSTALLMENTS_FIRST`) |
| `POST /loans/{id}/rate-changes` `{effectiveFrom*, annualRate*}` | Lãi thả nổi: áp dụng từ kỳ bắt đầu sau ngày hiệu lực |

- `direction`: `BORROW` (đi vay – nợ phải trả) / `LEND` (cho vay – tiền sẽ thu). `type`: `MORTGAGE`, `UNSECURED`,
  `INSTALLMENT`, `PERSONAL`. `repaymentMethod`: `EQUAL_PRINCIPAL` (gốc đều), `ANNUITY` (trả góp đều), `BULLET` (gốc
  cuối kỳ), `INTEREST_FREE`.
- Kỳ trả `status`: `PENDING`, `PARTIAL`, `PAID`, `OVERDUE`. Thông báo `INSTALLMENT_DUE_SOON` (trước 3 ngày),
  `INSTALLMENT_OVERDUE` (ngày sau hạn), `LOAN_PAID_OFF`.
- Khoản vay **không** tự trừ/cộng ví; nếu muốn phản ánh vào ví, app ghi thêm giao dịch thu/chi tương ứng.

```json
// installments[0] của preview ANNUITY 12tr, 12%/năm, 3 tháng
{"id":"b351a938-…","periodNo":1,"dueDate":"2026-12-01","principalDue":3960265,"interestDue":120000,"totalDue":4080265,
 "principalPaid":0,"interestPaid":0,"remaining":4080265,"status":"PENDING"}
```

## 12. Đầu tư

| Endpoint | Mô tả |
|---|---|
| `POST /investments` `{name*, symbol?, type*, currency?, note?}` | `type`: `STOCK`, `FUND`, `GOLD`, `CRYPTO`, `BOND`, `REAL_ESTATE`, `OTHER`. `symbol` được viết hoa. Tiền tệ không đổi được |
| `GET /investments`, `GET /investments/{id}` | Tài sản kèm định giá và `trades` |
| `PATCH /investments/{id}` `{name?, symbol?, note?}`, `DELETE /investments/{id}` | Xoá chỉ ngừng theo dõi; giao dịch đã ghi ở ví vẫn giữ |
| `POST /investments/{id}/buys` / `sells` `{quantity*, price*, fee?, walletId?, date?}` | `201 TradeResponse`. Số lượng lẻ tới 8 chữ số. Bán không quá số nắm giữ (`INSUFFICIENT_QUANTITY`) |
| `POST /investments/{id}/incomes` `{amount*, walletId?, categoryId?, date?}` | Cổ tức/coupon/tiền thuê. Có `categoryId` (danh mục **thu**, cần `walletId`) thì là khoản thu trong báo cáo |
| `PUT /investments/{id}/price` `{price*, date?}` | Cập nhật giá thị trường; giá cũ hơn giá đã có → `STALE_PRICE` |
| `GET /investments/portfolio?currency=VND` | Tổng danh mục quy đổi một tiền tệ, phân bổ theo loại |

- **Giá vốn bình quân**: mua cộng `số lượng × giá + phí`; bán trừ giá vốn bình quân phần bán, phần còn lại của tiền bán
  (đã trừ phí) là `realized`. `unrealized` = giá trị thị trường − giá vốn; `totalReturn` = unrealized + realized.
  Chưa có giá → giá trị = giá vốn, `priced:false`.
- `walletId` tuỳ chọn: có ví thì tiền đi qua sổ thu chi (`EXTERNAL_OUT/IN`, `cashStatus` `PENDING → RECORDED`); bị từ
  chối → `FAILED` nhưng giao dịch tài sản vẫn giữ (đã xảy ra ở công ty chứng khoán). Không có ví → `NONE`.

```json
// POST /investments/{id}/buys {"quantity":100,"price":100000,"fee":15000,"walletId":"…"} → 201
{"id":"25caaa34-…","type":"BUY","quantity":100,"price":100000,"fee":15000,"cash":10015000,"realized":null,
 "date":"2026-11-01","walletId":"1e1a4d7a-…","cashStatus":"PENDING","transactionId":null}
```

## 13. Báo cáo và dashboard

Tham số chung: `from`, `to` (mặc định tháng hiện tại, tối đa 10 năm), `currency` (mặc định `VND`). Chỉ **thu/chi**
được tính; chuyển tiền, điều chỉnh, khoản `excludeFromReport` bị bỏ qua; `EXTERNAL_*` chỉ có trong dòng tiền.

| Endpoint | Mô tả |
|---|---|
| `GET /reports/summary` | Tổng thu, chi, chênh lệch, `savingsRate` (%), so với **kỳ trước** (cùng số tháng nếu là tháng trọn, nếu không cùng số ngày), `incomeChange`/`expenseChange` (%; `null` khi kỳ trước = 0) |
| `GET /reports/categories?type=EXPENSE\|INCOME` | Theo danh mục, danh mục con nằm trong `children` của cha, kèm `percent` |
| `GET /reports/cash-flow?granularity=MONTH\|DAY` | Từng kỳ (kể cả kỳ trống): `income`, `expense`, `net`, `externalIn/Out`, `cashFlow`. `DAY` tối đa 366 ngày |
| `GET /reports/net-worth` | Ví + tiết kiệm + đầu tư + tiền gửi + cho vay − đi vay, theo nhóm `kind`: `WALLET`, `SAVING`, `INVESTMENT`, `DEPOSIT`, `LENT`, `BORROWED` |
| `GET /reports/transactions.csv` | File CSV (UTF-8 có BOM): `date,type,wallet,category,amount,currency,source,excluded` – lưu file rồi mở share sheet |
| `POST /reports/rebuild` | Nạp lại dữ liệu báo cáo (công cụ hỗ trợ, thường không cần) |
| `GET /dashboard` | Tổng quan tiền gửi & vay: tổng theo tiền tệ, sổ/khoản vay đang hoạt động, kỳ quá hạn, khoản sắp tới 30 ngày, dòng tiền dự kiến 12 tháng |

```json
// GET /reports/summary
{"from":"2026-11-01","to":"2026-11-30","currency":"VND","income":0,"expense":845000,"net":-845000,"savingsRate":null,
 "previousFrom":"2026-10-01","previousTo":"2026-10-31","previous":{"income":0,"expense":0,"net":0},
 "incomeChange":null,"expenseChange":null}

// GET /reports/categories?type=EXPENSE
{"from":"2026-11-01","to":"2026-11-30","type":"EXPENSE","currency":"VND","total":845000,
 "categories":[{"categoryId":"86624f81-…","name":"Hoá đơn & Tiện ích","amount":800000,"percent":94.67,
                "children":[{"categoryId":"ac752633-…","name":"Điện","amount":800000,"percent":94.67,"children":[]}]},
               {"categoryId":"da49d9f1-…","name":"Ăn uống","amount":45000,"percent":5.33,"children":[]}]}

// GET /reports/cash-flow
{"from":"2026-11-01","to":"2026-11-30","currency":"VND","granularity":"MONTH",
 "points":[{"period":"2026-11","income":0,"expense":845000,"net":-845000,"externalIn":0,"externalOut":11015000,
            "cashFlow":-11860000}]}

// GET /reports/net-worth
{"asOf":"2026-11-01","currency":"VND","assets":4140000,"liabilities":0,"netWorth":4140000,
 "groups":[{"kind":"WALLET","total":-6860000,"items":[{"id":"1e1a4d7a-…","name":"Tiền mặt","amount":-6860000,
                                                      "currency":"VND","converted":-6860000}]},
           {"kind":"SAVING","total":1000000,"items":[…]},{"kind":"INVESTMENT","total":10000000,"items":[…]}]}
```

## 14. Thông báo và push

| Endpoint | Mô tả |
|---|---|
| `GET /notifications` | Mới nhất trước: `{id, type, title, body, referenceType, referenceId, createdAt, read}` (tiêu đề/nội dung tiếng Việt) |
| `GET /notifications/unread-count` | `{"count": 3}` – dùng cho badge |
| `POST /notifications/{id}/read` → 204, `POST /notifications/read-all` → `{"marked": n}` | |
| `GET/PUT /notifications/preferences` | `{disabledTypes, dailyReminderEnabled, dailyReminderTime, quietStart, quietEnd}` (+ `timeZone` khi đọc). Loại bị tắt không được tạo; trong giờ yên lặng (có thể qua nửa đêm) thông báo vẫn lưu nhưng không push |
| `POST /notifications/devices` `{token*, provider?, platform?, deviceName?}`, `GET`, `DELETE /notifications/devices/{token}` | Đăng ký token push của thiết bị (`provider`: `FCM` cho app KMP, `EXPO` cho app Expo; bỏ trống thì backend nhận ra từ token); xoá khi đăng xuất |

`type`: `DEPOSIT_MATURING_SOON`, `DEPOSIT_MATURED`, `INSTALLMENT_DUE_SOON`, `INSTALLMENT_OVERDUE`, `LOAN_PAID_OFF`,
`BUDGET_WARNING`, `BUDGET_EXCEEDED`, `BILL_DUE_SOON`, `BILL_OVERDUE`, `BILL_PAYMENT_FAILED`, `DAILY_REMINDER` (nhắc ghi
chép khi cả ngày chưa ghi gì, theo giờ người dùng chọn), `SAVING_GOAL_MILESTONE`. Dùng `referenceType` + `referenceId`
để điều hướng khi bấm thông báo (ví dụ mở hoá đơn, mục tiêu, khoản vay).

### 14.1 Push qua FCM cho app KMP

1. Tạo dự án **Firebase**, thêm app Android (package name) và app iOS (bundle id); với iOS tải **APNs Authentication
   Key** (.p8) lên Firebase (Project settings → Cloud Messaging). Backend bật FCM bằng `FCM_ENABLED=true` và file
   service account (xem `backend/README.md`, `docs/DEPLOYMENT.md`).
2. App lấy **FCM registration token**: Android `FirebaseMessaging.getInstance().token`; iOS dùng Firebase iOS SDK
   (`Messaging.messaging().token`) sau khi xin quyền thông báo và đăng ký APNs. Trong KMP, đặt hai cách lấy token này
   sau một `expect/actual`.
3. Sau khi đăng nhập: `POST /notifications/devices` `{"token":"<FCM token>","provider":"FCM","platform":"android"|"ios",
   "deviceName":"…"}` → `201 {token, provider:"FCM", …}`. Gọi lại khi SDK báo token mới (`onNewToken` /
   `messaging:didReceiveRegistrationToken`); gọi lại với token cũ là vô hại. Khi đăng xuất: `DELETE
   /notifications/devices/{token}`.
4. Tin nhắn FCM có `notification` (`title`, `body` – hệ điều hành tự hiển thị khi app ở nền) và `data` (chuỗi):
   `notificationId`, `type`, `referenceType`, `referenceId` – dùng để mở đúng màn hình khi người dùng bấm.
   Android gửi với kênh `channel_id = "default"` và mức ưu tiên cao: **tạo notification channel id `default`** khi khởi
   động app (Android 8+); iOS có `aps.sound = "default"`.
5. Token bị FCM báo `UNREGISTERED` (gỡ app) được backend tự xoá. Giờ yên lặng và loại bị tắt trong cài đặt áp dụng
   như mọi kênh. Khi app đang mở (foreground), FCM không tự hiện thông báo: app tự hiển thị hoặc làm mới danh sách và
   `unread-count`.

## 15. Gợi ý triển khai phía KMP

**Thư viện**: Ktor Client (OkHttp trên Android, Darwin trên iOS) + `ContentNegotiation` kotlinx.serialization,
kotlinx-coroutines, kotlinx-datetime, Koin, Compose Multiplatform (+ Navigation, Lifecycle ViewModel), Room KMP hoặc
SQLDelight (cache), DataStore (cài đặt), bignum (`com.ionspin.kotlin:bignum`) cho tiền.

**Cấu hình JSON**: `Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }` – backend có thể
thêm trường mới bất cứ lúc nào. Enum: thêm giá trị dự phòng (`UNKNOWN`) hoặc parse an toàn để app cũ không vỡ khi backend
thêm loại mới.

**Tiền**: không dùng `Double`. Viết `KSerializer<BigDecimal>` đọc **nội dung gốc** của số JSON
(`(decoder as JsonDecoder).decodeJsonElement().jsonPrimitive.content`) và ghi lại bằng `JsonUnquotedLiteral` để giữ
nguyên độ chính xác. Hiển thị theo tiền tệ (VND không lẻ, dấu chấm ngăn cách nghìn theo locale `vi`).

**Xác thực với Ktor**:

```kotlin
install(Auth) {
    bearer {
        loadTokens { tokenStore.read()?.let { BearerTokens(it.access, it.refresh) } }
        refreshTokens {
            // một luồng refresh tại một thời điểm (Ktor đã tuần tự hoá); refresh token dùng một lần
            val r = client.post("$base/api/v1/auth/refresh") {
                markAsRefreshTokenRequest(); setBody(RefreshRequest(oldTokens!!.refreshToken!!))
            }
            if (r.status == HttpStatusCode.OK) r.body<TokenPair>().also(tokenStore::save).let {
                BearerTokens(it.accessToken, it.refreshToken)
            } else { tokenStore.clear(); sessionEvents.emit(LoggedOut); null }
        }
        sendWithoutRequest { it.url.encodedPath.startsWith("/api/v1/auth/") }
    }
}
```

Lưu token ở Android Keystore (EncryptedSharedPreferences/DataStore mã hoá) và iOS Keychain qua `expect/actual`. Khi
refresh trả `401` → xoá token, về màn đăng nhập.

**Lỗi**: map mọi response không 2xx thành `ApiException(status, code, message, fieldErrors)` (body có thể rỗng với
`401`); tầng UI hiển thị theo `code`, đặt `fieldErrors` lên từng ô nhập.

**Idempotency**: sinh UUID khi người dùng bấm "Lưu" và giữ nó cho mọi lần thử lại của thao tác đó (kể cả sau khi app
khởi động lại nếu bạn có hàng đợi offline).

**Bất đồng bộ**: sau `202`/`PENDING`, poll đối tượng (1–2 giây, tối đa ~10 giây) rồi làm mới ví và báo cáo.

**Ngày giờ**: dùng `LocalDate` cho mọi trường ngày; "hôm nay" theo `timeZone` trong hồ sơ người dùng.

**Cấu trúc gợi ý**: `core/network` (Ktor, auth, lỗi), `core/model` (Money, Currency), `core/storage`, `core/ui`;
`feature/{auth, wallet, transaction, budget, bill, saving, deposit, loan, investment, report, notification}` – khớp với
module backend và các mục 4–14 của tài liệu này.

**Sinh code từ OpenAPI (tuỳ chọn)**: [`docs/api/openapi.json`](api/openapi.json) dùng được với OpenAPI Generator
(`kotlin` + `library=multiplatform`) để sinh DTO. Vẫn nên tự viết lớp repository/use case; nhớ thay kiểu số tiền
(`number` → `BigDecimal` bignum). Xuất lại file khi backend đổi: chạy backend rồi
`curl -s localhost:8080/v3/api-docs | jq . > docs/api/openapi.json`.

## 16. Việc backend cần làm thêm cho app KMP

| Việc | Vì sao | Mức |
|---|---|---|
| ~~Adapter push FCM~~ | **Đã có** (mục 14.1): bật bằng `FCM_ENABLED=true` + file service account Firebase | – |
| **Sign in with Apple** (`POST /auth/apple`) | App Store (guideline 4.8) thường yêu cầu khi app iOS có đăng nhập Google | Cần trước khi phát hành iOS nếu giữ đăng nhập Google |
| Khai báo client id Google của app Android/iOS vào `GOOGLE_CLIENT_IDS` | Để `/auth/google` chấp nhận ID token của app | Cấu hình |
| CORS | Không cần cho app native | – |

## 17. Dùng tài liệu này với Claude Code CLI

> Quy trình đầy đủ (chuẩn bị, `CLAUDE.md`, prompt cho từng đợt, prompt review/sửa lỗi, checklist phát hành):
> [KMP_CLAUDE_CODE_WORKFLOW.md](KMP_CLAUDE_CODE_WORKFLOW.md). Phần dưới là bản rút gọn.

1. Tạo repo app (ví dụ `finance-management-mobile`) và chép vào đó: `docs/MOBILE_API_GUIDE.md`,
   `docs/api/openapi.json` (giữ nguyên đường dẫn `docs/…`). Hoặc clone repo backend cạnh repo app và trỏ Claude tới đó.
2. Chạy backend local (mục 1), mở terminal trong repo app, chạy `claude`.
3. Yêu cầu đầu tiên gợi ý:

   ```text
   Đọc docs/MOBILE_API_GUIDE.md và docs/api/openapi.json (API của backend Finance Manage).
   Tạo project Kotlin Multiplatform cho Android + iOS dùng Compose Multiplatform, Ktor, kotlinx.serialization,
   kotlinx-datetime, Koin, Room KMP, bignum; package com.financemanage.mobile.
   1. Viết docs/PLAN.md: kiến trúc (core/*, feature/* theo mục 15) và lộ trình giai đoạn theo mục 4–14.
   2. Viết CLAUDE.md: quy ước code, cách build/test, quy tắc tiền (BigDecimal), lỗi, idempotency, PENDING.
   3. Làm giai đoạn 0: khung project chạy được trên Android emulator và iOS simulator; core/network với auth
      (refresh token một lần), map lỗi, Idempotency-Key; màn đăng ký/đăng nhập/2FA nối backend
      http://10.0.2.2:8080 (Android) / http://localhost:8080 (iOS); unit test cho tầng network bằng MockEngine.
   Build và chạy test trước khi báo xong.
   ```

4. Các lần sau: *"Làm tiếp giai đoạn N theo docs/PLAN.md, đối chiếu mục X của docs/MOBILE_API_GUIDE.md"*. Mỗi giai đoạn
   nên kết thúc bằng build Android + iOS và một lần chạy thử với backend thật (dùng
   `GET /api/v1/dev/mailbox?to=<email>` để lấy mã email khi test đăng ký/quên mật khẩu).
