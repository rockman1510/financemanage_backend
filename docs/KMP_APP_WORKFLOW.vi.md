# Workflow xây app Finance Manage (KMP) bằng Claude Code CLI

> Bản tiếng Anh: [KMP_APP_WORKFLOW.en.md](KMP_APP_WORKFLOW.en.md)

Một workflow duy nhất để Claude Code CLI trên máy tính xây **toàn bộ** app quản lý tài chính cá nhân cho **Android +
iOS** bằng Kotlin Multiplatform, nối với backend trong repo này.

- **Phần A** – chuẩn bị (làm một lần).
- **Phần B** – **prompt chính**: chép-dán một lần; Claude làm lần lượt mọi tính năng theo blueprint, dừng sau mỗi
  tính năng để bạn kiểm tra.
- **Phần C** – **blueprint** của từng tính năng: màn hình, thành phần, luồng, API, quy tắc, lỗi, tiêu chí hoàn thành.

Tài liệu API: [MOBILE_API_GUIDE.md](MOBILE_API_GUIDE.md) (gọi tắt **API §n** = mục n của file này) và
[api/openapi.json](api/openapi.json).

---

## Phần A – Chuẩn bị

1. **Công cụ**: Claude Code CLI (`npm install -g @anthropic-ai/claude-code`), JDK 21, Android Studio (Android SDK +
   emulator, plugin Kotlin Multiplatform), **macOS + Xcode** để build/chạy iOS (không có Mac: làm phần chung + Android,
   iOS kiểm qua CI), Docker Desktop, Git.
2. **Thư mục và tài liệu**:

   ```bash
   mkdir -p ~/projects && cd ~/projects
   git clone https://github.com/rockman1510/financemanage_backend.git
   git clone https://github.com/rockman1510/finance-management-mobile.git
   mkdir -p finance-management-mobile/docs/backend
   cp financemanage_backend/docs/MOBILE_API_GUIDE.md financemanage_backend/docs/api/openapi.json \
      financemanage_backend/docs/KMP_APP_WORKFLOW.vi.md finance-management-mobile/docs/backend/
   ```

3. **Backend local** (giữ chạy): `cd ~/projects/financemanage_backend && docker compose up --build` →
   `http://localhost:8080` (Android emulator: `http://10.0.2.2:8080`). Cần lấy mã email khi test thì chạy profile dev:
   `cd backend && ./gradlew :app:bootRun --args='--spring.profiles.active=dev'`, mã nằm ở
   `GET /api/v1/dev/mailbox?to=<email>`.
4. **Bớt hỏi quyền** – tạo `finance-management-mobile/.claude/settings.json`:

   ```json
   {"permissions": {"allow": ["Bash(./gradlew:*)", "Bash(git:*)", "Bash(xcodebuild:*)", "Bash(xcrun simctl:*)",
     "Bash(adb:*)", "Bash(curl:*)", "Bash(jq:*)"]}}
   ```

5. **Chạy**: `cd ~/projects/finance-management-mobile && claude`, bấm **Shift+Tab** để vào plan mode, dán **prompt
   chính** (Phần B). Khi Claude dừng sau một tính năng: chạy thử app, báo lỗi nếu có, rồi gõ `tiếp tục`.
   Phiên dài thì `/compact`; mở lại phiên bằng `claude --continue`; bắt đầu phiên mới thì dán lại prompt chính –
   Claude tiếp tục từ tính năng chưa xong trong `docs/PLAN.md`.

---

## Phần B – Prompt chính

```text
Bạn là kỹ sư mobile chính của app Finance Manage – app quản lý tài chính cá nhân tiếng Việt cho Android và iOS,
viết bằng Kotlin Multiplatform + Compose Multiplatform, nối với backend REST có sẵn.

TÀI LIỆU (đọc kỹ trước khi làm, tra lại mỗi khi cần, không đoán API):
- docs/backend/KMP_APP_WORKFLOW.vi.md – Phần C là BLUEPRINT của mọi tính năng (C0 nền tảng chung, F01–F22).
- docs/backend/MOBILE_API_GUIDE.md – cách dùng API, quy tắc nghiệp vụ, mã lỗi, JSON mẫu ("API §n" = mục n).
- docs/backend/openapi.json – kiểu dữ liệu chính xác của request/response.

QUY TRÌNH:
1. Khởi tạo (nếu chưa có):
   a. Viết CLAUDE.md từ blueprint C0 (công nghệ, kiến trúc, quy ước, lệnh build/test thực tế).
   b. Viết docs/PLAN.md: bảng F01–F22 theo đúng thứ tự trong blueprint, mỗi tính năng có các màn hình, endpoint
      và checklist "Tiêu chí hoàn thành" chép từ blueprint; cột trạng thái (chưa làm / đang làm / xong).
   c. Dựng khung project theo C0 (module, DI, theme, điều hướng 5 tab, baseUrl theo platform, CI Android + iOS),
      build được.
2. Lần lượt từng tính năng F01 → F22 (bỏ qua tính năng đã "xong" trong docs/PLAN.md):
   a. Đọc blueprint của tính năng + các mục API được nhắc tới; trình bày kế hoạch ngắn (file, màn hình, test).
   b. Làm đủ mọi màn hình, thành phần, luồng, quy tắc và xử lý lỗi trong blueprint – không bỏ bớt, không làm thêm
      tính năng ngoài blueprint.
   c. Viết test theo mục "Test" của blueprint.
   d. Build Android, chạy toàn bộ test; trên macOS build thêm iOS simulator. Sửa đến khi xanh.
   e. Tự review thay đổi theo CLAUDE.md (tiền BigDecimal, Idempotency-Key, PENDING, lỗi, loading/empty/error).
   f. Đánh dấu checklist trong docs/PLAN.md, commit với message "feat(Fxx): <tên tính năng>".
   g. DỪNG: báo tóm tắt (đã làm gì, cách kiểm tra trên emulator với backend thật, điều chưa chắc chắn) và chờ tôi
      trả lời "tiếp tục" hoặc báo lỗi. Nếu tôi báo lỗi: tìm nguyên nhân gốc, sửa, thêm test, rồi dừng lại lần nữa.
3. Nếu một tính năng cần backend làm thêm (ví dụ endpoint mới): không tự giả lập dữ liệu, ghi vào docs/PLAN.md mục
   "Yêu cầu backend" và báo tôi.
4. Khi mọi tính năng xong: chạy checklist phát hành (blueprint F22) và báo kết quả.

Bắt đầu với bước 1.
```

---

## Phần C – Blueprint

### C0. Nền tảng chung (áp dụng cho mọi tính năng)

**Công nghệ**: Kotlin Multiplatform, Compose Multiplatform (Material 3), Navigation Compose, Lifecycle ViewModel,
Ktor Client (OkHttp / Darwin) + kotlinx.serialization, kotlinx-coroutines, kotlinx-datetime, Koin, Room KMP,
DataStore, `com.ionspin.kotlin:bignum`, Coil 3, Vico (hoặc Canvas) cho biểu đồ, Firebase Messaging, ktlint/detekt.
Package gốc `com.financemanage.mobile`; phiên bản khai báo trong `gradle/libs.versions.toml`.

**Kiến trúc module**

| Module | Vai trò |
|---|---|
| `composeApp` | Điểm vào Android/iOS, NavHost, theme, khởi tạo Koin |
| `core/model` | `Money`, `CurrencyCode`, `AppError`, kiểu dùng chung |
| `core/network` | HttpClient, auth/refresh, map lỗi, Idempotency-Key, poll PENDING |
| `core/data` | Room, DataStore, lưu token an toàn (Keystore/Keychain – expect/actual), trạng thái mạng |
| `core/ui` | Design system: màu, chữ, `MoneyText`, `AmountKeypad`, `CategoryIcon`, `ProgressBar`, `EmptyState`, `ErrorState`, skeleton, `ConfirmDialog`, `DateRangePicker`, biểu đồ |
| `core/testing` | Fake, MockEngine, JSON mẫu |
| `feature/*` | Mỗi tính năng: `data` (DTO, Api, Repository) / `domain` (model, use case) / `presentation` (ViewModel, Screen). Feature không phụ thuộc feature khác |
| `iosApp` | Project Xcode |

**Điều hướng**

- Chưa đăng nhập: Chào → Đăng nhập / Đăng ký / Quên mật khẩu → (Onboarding lần đầu).
- Đã đăng nhập – bottom bar 5 mục: **Tổng quan** · **Sổ giao dịch** · **➕** (bottom sheet: Chi, Thu, Chuyển tiền) ·
  **Kế hoạch** (tab trên: Ngân sách, Hoá đơn, Tiết kiệm) · **Tài sản** (Ví, Sổ tiết kiệm, Vay & cho vay, Đầu tư, Tài sản
  ròng).
- Thanh trên của Tổng quan: chuông thông báo (badge số chưa đọc), ảnh đại diện → Cài đặt. Báo cáo mở từ Tổng quan.
- Deep link `financemanage://<referenceType>/<id>` dùng khi bấm thông báo.

**Quy ước bắt buộc**

- **Tiền**: BigDecimal (bignum), không dùng `Double`/`Float`. Serializer đọc nội dung gốc của số JSON. Hiển thị theo
  `vi-VN`: `1.250.000 ₫`, `12,50 US$`; VND 0 chữ số lẻ, USD 2. Âm màu đỏ, dương màu xanh. Gửi lên đúng số lẻ của tiền tệ.
- **Ngày**: `LocalDate` `yyyy-MM-dd`; "hôm nay" theo `timeZone` của hồ sơ; hiển thị `dd/MM/yyyy`, nhóm "Hôm nay",
  "Hôm qua".
- **JSON**: `ignoreUnknownKeys = true`, `explicitNulls = false`; enum có `UNKNOWN` dự phòng.
- **Auth**: access token 15 phút; refresh token dùng **một lần** (Ktor `Auth` bearer + `refreshTokens`, chỉ một refresh
  chạy cùng lúc); refresh thất bại → xoá token, phát sự kiện đăng xuất, về màn Đăng nhập.
- **Lỗi**: response không 2xx → `AppError(status, code, message, fieldErrors)`; UI dịch `code` sang tiếng Việt (bảng mã
  API §3), `fieldErrors` hiện dưới từng ô; `409` → tải lại rồi thử lại; `429` → hiện thời gian chờ; mất mạng → banner.
- **Idempotency-Key**: UUID sinh khi bấm Lưu, giữ cho mọi lần thử lại của cùng thao tác: `POST /transactions`,
  `/transfers`, `/loans/{id}/repayments`, `/loans/{id}/prepayments`.
- **PENDING** (API §2.1): sau `202`/`PENDING` hiện "Đang xử lý…", poll đối tượng mỗi 1,5 giây tối đa 10 giây; `RECORDED`
  → báo thành công + làm mới ví; `FAILED` → hiện `failureReason`; hết giờ → "Sẽ cập nhật sau", làm mới khi quay lại màn.
- **Màn hình**: mọi màn có loading (skeleton), empty (minh hoạ + nút hành động), error (nút thử lại); kéo để làm mới;
  làm mới khi app quay lại foreground. State bất biến + Event + ViewModel; không gọi API trong Composable.
- **Chuỗi** tiếng Việt trong resources (chuẩn bị sẵn tiếng Anh). Hỗ trợ chế độ tối, cỡ chữ động, `contentDescription`.
- **Bí mật**: không commit `google-services.json`, `GoogleService-Info.plist`, keystore.

**Lệnh** (ghi vào CLAUDE.md, chỉnh theo project thực tế): `./gradlew :composeApp:assembleDebug`,
`./gradlew :composeApp:installDebug`, `./gradlew allTests`, `./gradlew ktlintCheck detekt`; iOS:
`./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` rồi
`xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build`.

**CI**: GitHub Actions – build + test Android (ubuntu-latest), build iOS (macos-latest).

---

### F01. Nền tảng app

- **Mục tiêu**: khung chạy được để mọi tính năng sau cắm vào.
- **Thành phần**: `HttpClient` (baseUrl: Android emulator `10.0.2.2:8080`, iOS simulator `localhost:8080`, production
  `https://<DOMAIN>` theo build type; cleartext/ATS chỉ cho debug), `Auth` plugin, `ErrorMapper`, `IdempotencyKeys`,
  `PendingPoller`, `TokenStore` (expect/actual), `NetworkMonitor`, `MoneyFormatter`, `MoneySerializer`, design system
  `core/ui`, NavHost với 5 tab (màn trống), theme sáng/tối.
- **API**: §1, §2, §3.
- **Tiêu chí hoàn thành**: app chạy trên Android + iOS hiện 5 tab; `MoneyFormatter` đúng cho VND/USD; mọi lỗi HTTP map
  ra `AppError`; CI xanh.
- **Test**: MoneySerializer (giữ nguyên `40.00`, `94.67`), MoneyFormatter, ErrorMapper (400 có fieldErrors, 401 rỗng,
  422, 429), Auth refresh (một lần, 401 khi refresh → đăng xuất) bằng MockEngine.

### F02. Đăng ký, đăng nhập

- **Màn hình**: Chào (logo, Đăng nhập, Tạo tài khoản) · Đăng ký (họ tên, email, mật khẩu 8–72 có hiện/ẩn) · Đăng nhập
  (email, mật khẩu, Quên mật khẩu?, nút **Google chỉ trên Android**) · Xác thực 2 bước (mã 6 số tự nhảy ô; link "Dùng mã
  khôi phục") · Quên mật khẩu (email → nhập mã 6 số + mật khẩu mới).
- **Luồng**: đăng ký thành công → Onboarding (F03); đăng nhập trả `twoFactorRequired` → màn 2FA với `challengeToken`
  (hết hạn 5 phút → quay lại đăng nhập); Google: Credential Manager lấy ID token → `/auth/google`; đặt lại mật khẩu xong
  → về Đăng nhập kèm thông báo; gửi `deviceName` (tên máy).
- **API**: §4.1 – `POST /auth/register`, `/auth/login`, `/auth/2fa/verify`, `/auth/google`, `/auth/forgot-password`,
  `/auth/reset-password`, `/auth/logout`.
- **Quy tắc & lỗi**: `EMAIL_TAKEN` dưới ô email; `INVALID_CREDENTIALS` "Email hoặc mật khẩu không đúng";
  `ACCOUNT_LOCKED` (429) "Tạm khoá 15 phút"; `INVALID_CODE`; `GOOGLE_SIGN_IN_DISABLED`; quên mật khẩu luôn báo "Nếu email
  tồn tại, mã đã được gửi".
- **Tiêu chí hoàn thành**: đăng ký, đăng nhập, 2FA, quên/đặt lại mật khẩu chạy với backend thật; mở lại app vẫn đăng
  nhập; refresh token hết hạn → về Đăng nhập không lặp.
- **Test**: ViewModel đăng nhập (thường, 2FA, lỗi), repository lưu/xoá token.

### F03. Onboarding

- **Màn hình** (3 bước, có thanh tiến độ, bỏ qua được bước 3): (1) Chọn tiền tệ mặc định (VND mặc định, tìm kiếm mã
  ISO) · (2) Tạo ví đầu tiên (tên gợi ý "Tiền mặt", loại, số dư hiện có bằng `AmountKeypad`) · (3) Tạo ngân sách tháng
  (số tiền cho mọi khoản chi).
- **API**: `PATCH /me` (defaultCurrency), `POST /wallets`, `POST /budgets` (§4.2, §5.1, §7).
- **Tiêu chí hoàn thành**: chỉ hiện một lần (cờ trong DataStore + kiểm tra đã có ví); xong → Tổng quan có dữ liệu.

### F04. Hồ sơ, cài đặt, bảo mật

- **Màn hình Cài đặt**: thẻ hồ sơ (tên, email, nhãn "Chưa xác thực") · Hồ sơ (họ tên, tiền tệ mặc định, ngôn ngữ, múi
  giờ, ngày bắt đầu tháng 1–28) · Xác thực email (gửi mã, nhập mã; đếm ngược 60 giây trước khi gửi lại) · Mật khẩu (đổi;
  tài khoản chỉ có Google: "Đặt mật khẩu" không cần mật khẩu cũ) · Xác thực 2 bước (bật: hiện QR từ `otpauthUri` + mã
  `secret` để chép → nhập mã → hiện **10 mã khôi phục một lần** có nút chép/lưu, bắt xác nhận "Tôi đã lưu"; tắt: nhập
  mã) · Thiết bị đăng nhập (danh sách, đánh dấu máy này, thu hồi từng máy, "Đăng xuất mọi máy khác") · Thông báo (F20) ·
  Tỷ giá (F11) · Khoá app (công tắc vân tay/Face ID, thời gian khoá lại 1/5/15 phút) · Ẩn số dư mặc định · Giao diện
  (sáng/tối/hệ thống) · Đăng xuất · **Xoá tài khoản** (cảnh báo, nhập lại email + mật khẩu).
- **API**: §4.2 – `GET/PATCH /me`, `/me/email/*`, `/me/password`, `/me/2fa/*`, `/me/sessions`, `DELETE /me`.
- **Quy tắc & lỗi**: `WRONG_PASSWORD`, `INVALID_CODE`, `RESEND_TOO_SOON` (429), `CONFIRMATION_MISMATCH`; xoá tài khoản
  thành công → xoá dữ liệu local, về màn Chào. Khoá app dùng BiometricPrompt / LocalAuthentication (expect/actual),
  dự phòng mã PIN của máy.
- **Tiêu chí hoàn thành**: mọi mục chạy với backend thật; bật 2FA rồi đăng nhập lại phải qua màn 2FA; thu hồi máy khác
  làm máy đó bị đăng xuất khi refresh.
- **Test**: ViewModel 2FA (setup → enable → recovery codes), xoá tài khoản (xoá cache).

### F05. Ví

- **Màn hình**: Danh sách ví (tab Tài sản) – thẻ tổng quy đổi về tiền tệ mặc định (cảnh báo nếu `missingRates`), thẻ
  từng ví (icon, tên, số dư; thẻ tín dụng thêm thanh hạn mức đã dùng/còn lại, ngày đến hạn), mục "Đã lưu trữ" thu gọn ·
  Tạo/Sửa ví (tên ≤ 60, loại CASH/BANK_ACCOUNT/E_WALLET/CREDIT_CARD/OTHER, tiền tệ – chỉ khi tạo, số dư ban đầu, icon,
  không tính vào tổng; CREDIT_CARD: hạn mức, ngày sao kê, ngày đến hạn 1–31) · Chi tiết ví (số dư, giao dịch của ví –
  dùng lại danh sách F07, nút Điều chỉnh số dư, Lưu trữ/Bỏ lưu trữ, Xoá).
- **API**: §5.1 – `GET/POST /wallets`, `GET/PATCH/DELETE /wallets/{id}`, `POST /wallets/{id}/adjust-balance`,
  `GET /wallets/total`.
- **Quy tắc & lỗi**: số dư có thể âm; xoá ví có giao dịch → `WALLET_HAS_TRANSACTIONS` → gợi ý Lưu trữ; ví lưu trữ không
  chọn được khi ghi giao dịch; điều chỉnh bằng số dư hiện tại → `NOTHING_TO_ADJUST`.
- **Tiêu chí hoàn thành**: tạo/sửa/lưu trữ/xoá/điều chỉnh đúng; tổng quy đổi khớp backend; cache hiện ngay khi mở.
- **Test**: repository ví, ViewModel form (validate thẻ tín dụng).

### F06. Danh mục

- **Màn hình**: Quản lý danh mục (tab Chi/Thu; danh mục cha mở ra danh mục con; danh mục mặc định có icon/màu theo
  `code`) · Tạo/Sửa (tên ≤ 60, loại, danh mục cha tuỳ chọn – tối đa 2 cấp, chọn icon) · Bộ chọn danh mục dùng chung
  (lưới icon, "Gần đây" ở đầu, tìm kiếm).
- **API**: §5.2 – `GET /categories?type=`, `POST`, `PATCH`, `DELETE /categories/{id}`.
- **Quy tắc & lỗi**: `CATEGORY_IN_USE`, `CATEGORY_HAS_CHILDREN`, `CATEGORY_TOO_DEEP`, `CATEGORY_TYPE_MISMATCH`; bảng ánh
  xạ `code` → icon + màu trong `core/ui` (FOOD, FOOD_RESTAURANT, FOOD_COFFEE, BILLS_*, TRANSPORT_*, SHOPPING_*,
  ENTERTAINMENT, HEALTH, EDUCATION, FAMILY, FRIENDS, GIFTS_DONATIONS, INSURANCE, FEES, OTHER_EXPENSE, SALARY, BONUS,
  INTEREST, SELLING, GIFTS_RECEIVED, OTHER_INCOME); danh mục tự tạo dùng icon người dùng chọn.
- **Tiêu chí hoàn thành**: bộ chọn dùng được ở mọi form; "Gần đây" lưu local.

### F07. Giao dịch thu chi

- **Màn hình Ghi nhanh** (từ ➕, bottom sheet toàn màn hình): công tắc Chi/Thu · số tiền lớn ở đầu + `AmountKeypad`
  (0–9, `000`, xoá, tự chèn dấu chấm nghìn) · bộ chọn danh mục (F06) · ví (mặc định ví dùng gần nhất) · ngày (mặc định
  hôm nay, chip Hôm qua) · "Thêm chi tiết": ghi chú ≤ 500, người liên quan ≤ 120, không tính vào báo cáo, ảnh hoá đơn
  (F10), lặp lại (F09) · nút Lưu (giữ nguyên sheet nếu bật "Ghi tiếp").
- **Màn hình Sổ giao dịch** (tab): thanh tìm kiếm + nút lọc · thẻ tổng thu/chi/chênh lệch theo bộ lọc · danh sách nhóm
  theo ngày (tiêu đề ngày + tổng ngày), mỗi dòng: icon danh mục, tên danh mục, ghi chú, ví, số tiền màu, nhãn nguồn
  (Hoá đơn / Tiết kiệm / Đầu tư / Sổ tiết kiệm / Chuyển tiền / Điều chỉnh) · cuộn vô hạn (size 30) · vuốt trái Sửa/Xoá.
- **Bộ lọc** (bottom sheet): ví, danh mục (nhiều), loại (nhiều), khoảng ngày (tháng này, tháng trước, tuỳ chọn), số tiền
  từ–đến, từ khoá; chip bộ lọc đang bật, nút xoá lọc.
- **Chi tiết giao dịch**: mọi trường, ảnh hoá đơn, nút Sửa/Xoá (ẩn với giao dịch chỉ xem).
- **API**: §5.3 – `POST /transactions` (+Idempotency-Key), `GET /transactions`, `GET /transactions/summary`,
  `GET/PATCH/DELETE /transactions/{id}`.
- **Quy tắc & lỗi**: số tiền > 0, đúng số lẻ tiền tệ (`INVALID_AMOUNT`); giao dịch có `source`, chuyển tiền, điều chỉnh,
  EXTERNAL_* chỉ xem (`TRANSACTION_NOT_EDITABLE`); `WALLET_ARCHIVED`; sửa/xoá xong làm mới ví, ngân sách, tổng quan.
- **Tiêu chí hoàn thành**: ghi một khoản chi ≤ 3 chạm sau khi mở ➕; lọc kết hợp nhiều điều kiện khớp backend; gửi lại
  khi timeout không tạo bản trùng.
- **Test**: keypad (định dạng, 000, xoá), ViewModel ghi (Idempotency-Key giữ nguyên khi thử lại), phân trang, bộ lọc →
  query string.

### F08. Chuyển tiền giữa ví

- **Màn hình** (➕ → Chuyển tiền): ví nguồn, ví đích (không trùng), số tiền; khác tiền tệ: hiện tỷ giá ước tính (F11) và
  ô "Số tiền nhận" sửa được; ngày, ghi chú.
- **API**: §5.4 – `POST /transfers` (+Idempotency-Key), `DELETE /transfers/{transferId}`.
- **Quy tắc & lỗi**: `SAME_WALLET`, `EXCHANGE_RATE_MISSING` (gợi ý nhập số tiền nhận hoặc thêm tỷ giá),
  `TRANSFER_AMOUNT_MISMATCH`; xoá một chiều = xoá cả hai.
- **Tiêu chí hoàn thành**: số dư hai ví đổi đúng; chuyển khác tiền tệ đúng theo tỷ giá hoặc số tiền nhận.

### F09. Giao dịch định kỳ

- **Màn hình**: công tắc "Lặp lại" trong Ghi nhanh (tần suất Hằng ngày/Tuần/Tháng/Năm, mỗi N kỳ, ngày bắt đầu, ngày kết
  thúc tuỳ chọn, câu mô tả "Mỗi 2 tháng vào ngày 15") · Danh sách định kỳ (trong Cài đặt hoặc Sổ giao dịch → menu):
  tên, số tiền, lần tới (`nextOccurrence`), số lần đã tạo, trạng thái · Sửa (ví, danh mục, số tiền, ghi chú, ngày kết thúc,
  tạm dừng) · Xoá.
- **API**: §5.5 – `/recurring-transactions`.
- **Tiêu chí hoàn thành**: tạo định kỳ bắt đầu hôm nay → giao dịch xuất hiện sau khi job chạy; tạm dừng/tiếp tục được.

### F10. Ảnh hoá đơn

- **Thành phần**: hàng thumbnail trong Ghi nhanh và Chi tiết giao dịch; nút thêm → Chụp ảnh / Chọn từ thư viện / Chọn
  PDF; xem toàn màn hình (phóng to); xoá.
- **Kỹ thuật**: camera + thư viện qua expect/actual; nén JPEG < 5 MB; upload multipart phần `file`; tải ảnh bằng Ktor
  có header Authorization rồi hiển thị (cache đĩa).
- **API**: §5.6 – `POST /transactions/{id}/attachments`, `GET/DELETE /attachments/{id}`.
- **Quy tắc & lỗi**: JPEG/PNG/WebP/HEIC/PDF; `ATTACHMENT_TOO_LARGE`, `ATTACHMENT_TYPE_NOT_ALLOWED`,
  `TOO_MANY_ATTACHMENTS` (tối đa 5); khi tạo giao dịch mới: lưu giao dịch trước rồi upload ảnh, hiện tiến độ.
- **Tiêu chí hoàn thành**: chụp → lưu → mở lại thấy ảnh trên cả hai nền tảng.

### F11. Tiền tệ & tỷ giá

- **Màn hình** (Cài đặt → Tỷ giá): danh sách cặp tỷ giá (cặp, tỷ giá, ngày hiệu lực, nhãn "Chung" nếu `global`), thêm
  (`1 USD = 25.000 VND`, ngày hiệu lực), xoá · Công cụ quy đổi (số tiền, từ, sang, ngày).
- **Dùng chung**: hàm "Thiếu tỷ giá" – mọi nơi gặp `EXCHANGE_RATE_MISSING` hiện thông báo kèm nút "Thêm tỷ giá" mở
  sẵn cặp tiền tệ.
- **API**: §6 – `/exchange-rates`, `/exchange-rates/convert`.

### F12. Tổng quan (Home)

- **Thành phần** (cuộn dọc, mỗi khối tải độc lập, khối lỗi không làm hỏng cả màn):
  1. Thanh trên: lời chào, chuông (badge `unread-count`), ảnh đại diện.
  2. Thẻ tổng số dư (quy đổi) + nút con mắt ẩn mọi số tiền (nhớ lựa chọn).
  3. Thu/chi tháng này + % so với tháng trước (`/reports/summary`) → chạm mở Báo cáo.
  4. Ngân sách: 3 ngân sách có % cao nhất (thanh màu) → "Xem tất cả".
  5. Hoá đơn sắp đến hạn 7 ngày (`/bills/upcoming?days=7`) với nút Trả nhanh.
  6. Mục tiêu tiết kiệm gần nhất với tiến độ.
  7. 5 giao dịch gần nhất → "Xem tất cả".
- **API**: §5.1, §7, §8, §9, §13, §14. (Nếu backend thêm `GET /api/v1/home` thì chuyển sang gọi một request.)
- **Tiêu chí hoàn thành**: mở app thấy dữ liệu cache ngay rồi cập nhật; kéo để làm mới; ẩn số dư áp dụng mọi màn.

### F13. Ngân sách

- **Màn hình**: Danh sách (Kế hoạch → Ngân sách): mỗi thẻ tên, kỳ (`01/11–30/11`), đã chi / hạn mức, thanh tiến độ màu
  theo `state` (ON_TRACK xanh, WARNING vàng ≥ 80%, EXCEEDED đỏ, NOT_STARTED/ENDED xám), dòng "Còn **98.500 ₫/ngày** trong
  30 ngày", cảnh báo `unconvertedCurrencies` · Chi tiết: biểu đồ đã chi theo ngày trong kỳ, nút xem kỳ trước/sau
  (`?date=`), danh sách giao dịch thuộc ngân sách (lọc F07 theo danh mục + ví + kỳ) · Tạo/Sửa: tên, số tiền, tiền tệ, kỳ
  (Tuần/Tháng/Quý/Năm/Tuỳ chọn – Tuỳ chọn cần ngày bắt đầu/kết thúc), lặp lại, danh mục (chọn nhiều; trống = mọi khoản
  chi), ví (một ví hoặc tất cả).
- **API**: §7 – `/budgets`, `/budgets/{id}?date=`.
- **Quy tắc**: danh mục cha gồm cả con; chi khác tiền tệ được quy đổi.
- **Tiêu chí hoàn thành**: ghi khoản chi → thanh tiến độ cập nhật; màu đúng ngưỡng.

### F14. Hoá đơn định kỳ

- **Màn hình**: Danh sách (Kế hoạch → Hoá đơn) chia nhóm Quá hạn (đỏ) / Sắp đến hạn (vàng, ≤ `remindDaysBefore`) / Sau
  này; mỗi dòng tên, số tiền, "còn N ngày"/"quá hạn N ngày", nút **Trả** · Chi tiết: thông tin, lịch sử thanh toán
  (trạng thái, ngày, giao dịch liên kết), nút Trả ngay / Bỏ qua kỳ này / Tạm dừng / Sửa / Xoá · Tạo/Sửa: mẫu nhanh (Điện,
  Nước, Internet, Điện thoại, Thuê nhà → danh mục BILLS_*), tên, số tiền, ví, danh mục chi, tần suất (Tuần/Tháng/Quý/Năm),
  mỗi N kỳ, ngày đến hạn đầu tiên, ngày kết thúc, nhắc trước N ngày, ghi chú · Trả (sheet): số tiền (mặc định), ví, ngày.
- **API**: §8 – `/bills`, `/bills/upcoming`, `/bills/{id}/pay` (202), `/bills/{id}/skip`.
- **Quy tắc & lỗi**: trả theo luồng PENDING (C0); `FAILED` → kỳ đó vẫn đến hạn; `BILL_FINISHED`.
- **Tiêu chí hoàn thành**: trả hoá đơn → khoản chi xuất hiện ở ví, hoá đơn chuyển kỳ sau.

### F15. Mục tiêu tiết kiệm

- **Màn hình**: Danh sách (Kế hoạch → Tiết kiệm): thẻ có vòng tiến độ %, đã có / mục tiêu, hạn chót, "Để dành
  **1.500.000 ₫/tháng**", nhãn Tự động / Làm tròn · Chi tiết: tiến độ lớn, nút Nạp / Rút, lịch sử (MANUAL/AUTO/ROUND_UP,
  trạng thái), cấu hình **Trích tự động** (số tiền, ví, Tuần/Tháng, ngày bắt đầu; tắt) và **Làm tròn chi tiêu** (ví, bội
  số 1.000/5.000/10.000; giải thích "Chi 47.000 → để dành 3.000"; tắt) · Tạo/Sửa: tên, số tiền mục tiêu, tiền tệ, hạn chót
  (xoá được), icon · Nạp/Rút (sheet): số tiền, ví (cùng tiền tệ), ngày.
- **API**: §9 – `/saving-goals`, `/deposits`, `/withdrawals` (202), `/auto-contribution`, `/round-up`.
- **Quy tắc & lỗi**: PENDING; `INSUFFICIENT_SAVINGS`, `GOAL_NOT_EMPTY` (rút hết mới xoá), `ROUND_UP_TAKEN`,
  `CURRENCY_MISMATCH`; hiệu ứng chúc mừng khi vượt 50% và 100%.
- **Tiêu chí hoàn thành**: nạp → ví giảm, mục tiêu tăng; làm tròn hoạt động khi ghi khoản chi ở ví đã chọn.

### F16. Sổ tiết kiệm (tiền gửi ngân hàng)

- **Màn hình**: Danh sách (Tài sản → Sổ tiết kiệm; lọc Đang gửi / Đã đáo hạn / Đã tất toán): ngân hàng, gốc, lãi suất,
  ngày đáo hạn (đếm ngược), lãi tích luỹ · Tạo: ngân hàng, số tài khoản, Có kỳ hạn/Không kỳ hạn, gốc, tiền tệ, lãi suất
  %/năm, lãi suất rút trước hạn, kỳ hạn (tháng), ngày gửi, cách trả lãi (cuối kỳ / hằng tháng / trả trước), tái tục (không
  / gốc / gốc + lãi), cách tính lãi, ghi chú, ví trích tiền (tuỳ chọn); **khối xem trước cập nhật khi gõ**: ngày đáo hạn,
  tiền lãi, tổng nhận · Chi tiết: lãi tích luỹ hôm nay, lãi dự kiến, lịch sử giao dịch sổ, trạng thái trích/nhận tiền ví
  · Tất toán (sheet): ngày, ví nhận; **báo giá** gốc + lãi = tổng, cảnh báo "Rút trước hạn: mất X ₫ tiền lãi".
- **API**: §10 – `/deposits`, `/deposits/preview`, `/deposits/{id}/settlement-quote`, `/deposits/{id}/settle`.
- **Quy tắc & lỗi**: PENDING cho `fundingStatus`/`payoutStatus`; `DEPOSIT_ALREADY_SETTLED`, `INVALID_SETTLEMENT_DATE`.
- **Tiêu chí hoàn thành**: số liệu xem trước khớp backend; tất toán trước hạn hiện đúng lãi mất.

### F17. Vay & cho vay

- **Màn hình**: Danh sách (Tài sản → Vay & cho vay; tab "Tôi đi vay" / "Tôi cho vay"; lọc Đang hoạt động / Đã tất toán):
  đối tác, dư nợ, kỳ tới + số tiền, nhãn "Quá hạn N kỳ" · Tạo: đối tác, đi vay/cho vay, loại, gốc, tiền tệ, lãi suất,
  số tháng, ngày bắt đầu, cách trả (gốc đều / trả góp đều / gốc cuối kỳ / không lãi), phí trả trước %, ghi chú; **lịch trả
  xem trước** (bảng kỳ + biểu đồ gốc/lãi, tổng lãi, tổng trả) · Chi tiết: dư nợ, lãi suất hiện tại, các kỳ
  (PENDING/PARTIAL/PAID/OVERDUE), lịch sử trả (gốc/lãi/phí), các giai đoạn lãi suất · Trả kỳ (số tiền, ngày) · Trả trước
  hạn (tiền gốc, ngày; hiện phí ước tính) · Đổi lãi suất (ngày hiệu lực, lãi suất mới).
- **API**: §11 – `/loans`, `/loans/preview-schedule`, `/repayments`, `/prepayments` (+Idempotency-Key), `/rate-changes`.
- **Quy tắc & lỗi**: `OVERPAYMENT`, `SETTLE_DUE_INSTALLMENTS_FIRST`, `LOAN_NOT_ACTIVE`, `INTEREST_FREE_LOAN`; ghi chú cho
  người dùng: khoản vay không tự đổi số dư ví (gợi ý ghi thêm giao dịch).
- **Tiêu chí hoàn thành**: lịch xem trước khớp backend; trả hết → trạng thái Đã tất toán.

### F18. Đầu tư

- **Màn hình**: Danh mục (Tài sản → Đầu tư): tổng giá trị, giá vốn, lãi/lỗ chưa thực hiện (₫, %, xanh/đỏ), đã thực hiện,
  tổng lợi nhuận, **biểu đồ tròn phân bổ** theo loại; chọn tiền tệ hiển thị · Danh sách tài sản: tên, mã, số lượng, giá
  trị, lãi/lỗ %, nhãn "Chưa có giá" khi `priced=false` · Chi tiết: số lượng, giá vốn bình quân, giá hiện tại + ngày giá,
  nút **Cập nhật giá**, lãi/lỗ, lịch sử giao dịch (Mua/Bán/Cổ tức, trạng thái tiền) · Tạo/Sửa tài sản: tên, mã, loại
  (Cổ phiếu, Quỹ, Vàng, Crypto, Trái phiếu, Bất động sản, Khác), tiền tệ (chỉ khi tạo), ghi chú · Mua/Bán: số lượng (lẻ tới
  8 chữ số), giá, phí, ngày, ví hoặc "Chỉ theo dõi"; dòng tự tính tổng tiền; bán hiện lãi/lỗ dự kiến · Cổ tức: số tiền, ví,
  danh mục thu (mặc định Tiền lãi), ngày.
- **API**: §12 – `/investments`, `/investments/portfolio`, `/buys`, `/sells`, `/incomes`, `/price`.
- **Quy tắc & lỗi**: PENDING theo `cashStatus`; `INSUFFICIENT_QUANTITY`, `FEE_TOO_HIGH`, `STALE_PRICE`,
  `EXCHANGE_RATE_MISSING` (danh mục nhiều tiền tệ).
- **Tiêu chí hoàn thành**: mua/bán/cổ tức cập nhật giá vốn và lãi/lỗ khớp backend.

### F19. Báo cáo & tài sản ròng

- **Màn hình Báo cáo** (từ Tổng quan): bộ chọn kỳ (Tháng này, Tháng trước, Quý, Năm, Tuỳ chọn; vuốt ngang đổi tháng),
  tiền tệ báo cáo · **Tổng quan kỳ**: thu, chi, chênh lệch, tỷ lệ tiết kiệm, mũi tên % so với kỳ trước · **Theo danh
  mục**: tab Chi/Thu, biểu đồ tròn + danh sách (tên, số tiền, %), chạm → danh mục con → giao dịch (F07 đã lọc) · **Dòng
  tiền**: biểu đồ cột theo tháng (hoặc ngày khi kỳ ≤ 31 ngày): thu, chi, chuyển sang tiết kiệm/đầu tư, đường dòng tiền
  ròng · **Xuất CSV**: chọn kỳ → tải → lưu file → share sheet.
- **Màn hình Tài sản ròng** (đầu tab Tài sản + chi tiết): số lớn, tài sản – nợ, nhóm Ví / Tiết kiệm / Đầu tư / Sổ tiết
  kiệm / Cho vay / Đi vay với từng mục (số gốc + quy đổi).
- **Thẻ Tiền gửi & vay** (trong Tài sản): dữ liệu `/dashboard` – khoản sắp đến hạn 30 ngày, dòng tiền dự kiến 12 tháng.
- **API**: §13 – `/reports/summary`, `/categories`, `/cash-flow`, `/net-worth`, `/transactions.csv`, `/dashboard`.
- **Quy tắc**: báo cáo cập nhật sau giao dịch vài trăm ms (làm mới khi quay lại màn); `INVALID_PERIOD`,
  `PERIOD_TOO_LONG`, `EXCHANGE_RATE_MISSING`.
- **Tiêu chí hoàn thành**: số liệu khớp backend; biểu đồ đọc được ở chế độ tối; CSV mở được trong Excel/Numbers.

### F20. Thông báo & push

- **Màn hình**: Danh sách thông báo (chuông): nhóm theo ngày, chưa đọc in đậm, icon theo `type`, chạm → đánh dấu đã đọc
  + điều hướng theo `referenceType`/`referenceId` (BILL → hoá đơn, BUDGET → ngân sách, SAVING_GOAL → mục tiêu, DEPOSIT →
  sổ, LOAN → khoản vay; DAILY_REMINDER không có tham chiếu → mở Ghi nhanh), nút "Đọc tất cả" · Cài đặt thông báo (Cài đặt → Thông báo): công tắc từng loại (12 loại, nhóm
  theo tính năng), nhắc ghi chép hằng ngày + giờ nhắc, khung giờ yên lặng (từ–đến), liên kết tới cài đặt hệ thống nếu
  quyền bị tắt.
- **Push (FCM)**: Firebase Messaging Android + iOS (expect/actual); xin quyền thông báo **sau khi người dùng tạo ngân
  sách hoặc hoá đơn đầu tiên**; sau đăng nhập và khi token đổi: `POST /notifications/devices`
  `{token, provider:"FCM", platform, deviceName}`; đăng xuất: `DELETE /notifications/devices/{token}`; Android tạo kênh
  `default`; bấm push → mở đúng màn (deep link C0); app ở foreground → làm mới badge và danh sách.
- **API**: §14, §14.1.
- **Tiêu chí hoàn thành**: nhận push thật trên Android và iOS (Firebase + APNs key), bấm mở đúng màn; badge đúng.

### F21. Offline & đồng bộ

- **Cache** (Room): ví, danh mục, 3 trang giao dịch đầu, ngân sách, hoá đơn, mục tiêu, thông báo, tổng quan; hiện cache
  ngay, làm mới nền, ghi "Cập nhật lúc HH:mm".
- **Hàng đợi ghi**: giao dịch thu/chi và chuyển tiền tạo khi offline được lưu kèm Idempotency-Key, hiện nhãn "Chờ gửi",
  tự gửi khi có mạng (gửi lại cùng key → không trùng); lỗi nghiệp vụ → đánh dấu lỗi để người dùng sửa.
- **Banner** mất mạng; các thao tác cần mạng (trả hoá đơn, nạp tiết kiệm, mua bán) bị tắt khi offline.
- **Tiêu chí hoàn thành**: bật chế độ máy bay → ghi 3 giao dịch → tắt chế độ máy bay → cả 3 lên server, không trùng.

### F22. Hoàn thiện & phát hành

- **Hoàn thiện**: rà loading/empty/error mọi màn; chế độ tối; cỡ chữ lớn; accessibility; hiệu năng danh sách dài; chuỗi
  tiếng Anh; icon, splash, tên app.
- **Phát hành**: baseUrl production HTTPS theo build type; ký app Android (AAB), signing iOS; CI tạo bản Play Internal
  Testing và TestFlight; `docs/RELEASE.md`; chính sách quyền riêng tư; mô tả quyền (camera, thông báo, Face ID) trong
  `Info.plist` và `AndroidManifest`.
- **Checklist phát hành**:
  - [ ] Mọi tính năng F01–F21 đã "xong" trong `docs/PLAN.md`, CI xanh Android + iOS.
  - [ ] Không có `Double`/`Float` cho tiền; định dạng `vi-VN` đúng.
  - [ ] Refresh token hết hạn / bị thu hồi → về Đăng nhập, không lặp.
  - [ ] Mạng chập chờn không tạo giao dịch trùng (Idempotency-Key).
  - [ ] Thao tác PENDING hiện đúng trạng thái và tự cập nhật.
  - [ ] Push nhận được trên máy thật, bấm mở đúng màn, đăng xuất thì hết nhận.
  - [ ] Không có bí mật trong git; mọi request production dùng HTTPS.
  - [ ] iOS không có nút Google (hoặc đã có Sign in with Apple); có chức năng xoá tài khoản trong app.
  - [ ] Chế độ tối, cỡ chữ lớn, VoiceOver/TalkBack đọc được các nút chính.

---

## Khi app cần backend làm thêm

Mở **phiên Claude Code riêng** trong thư mục `financemanage_backend` và mô tả yêu cầu, ví dụ:

```text
App mobile cần GET /api/v1/home trả trong một request: tổng ví quy đổi, thu/chi tháng này và tháng trước, 3 ngân sách
có % cao nhất, hoá đơn 7 ngày tới, mục tiêu tiết kiệm gần nhất, 5 giao dịch gần nhất, số thông báo chưa đọc. Làm theo
kiến trúc modular monolith hiện có, thêm test integration, cập nhật docs/MOBILE_API_GUIDE.md, xuất lại
docs/api/openapi.json, chạy ./gradlew build.
```

Sau khi backend xong: chép lại `MOBILE_API_GUIDE.md` và `openapi.json` sang `docs/backend/` của app rồi bảo Claude
(phiên app): "Tài liệu API vừa đổi, so sánh với bản trước bằng git diff và cập nhật app."

Các yêu cầu backend đã biết: `GET /api/v1/home` (F12), SSE `/api/v1/events` cho dữ liệu live (tuỳ chọn), `POST
/auth/apple` (chỉ khi bật Google trên iOS).
