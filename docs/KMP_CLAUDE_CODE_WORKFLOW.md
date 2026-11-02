# Quy trình xây app KMP bằng Claude Code CLI

Hướng dẫn từng bước để dùng **Claude Code CLI trên máy tính** xây app Finance Manage cho **Android + iOS** bằng
Kotlin Multiplatform, nối với backend trong repo này. Mỗi bước có **prompt chép-dán sẵn**.

- Tài liệu API app dùng: [`MOBILE_API_GUIDE.md`](MOBILE_API_GUIDE.md) + [`api/openapi.json`](api/openapi.json).
- Danh sách tính năng backend: [`FEATURE_SCOPE.md`](FEATURE_SCOPE.md).

## Mục lục

0. [Chuẩn bị](#0-chuẩn-bị)
1. [Cách làm việc với Claude Code](#1-cách-làm-việc-với-claude-code)
2. [Phiên khởi tạo: kế hoạch, quy ước, khung project](#2-phiên-khởi-tạo)
3. [Prompt cho từng đợt](#3-prompt-cho-từng-đợt) (Đợt 1–6)
4. [Prompt dùng lại](#4-prompt-dùng-lại) (review, sửa lỗi, test, khi backend đổi API…)
5. [Khi cần sửa backend](#5-khi-cần-sửa-backend)
6. [Checklist trước khi phát hành](#6-checklist-trước-khi-phát-hành)

---

## 0. Chuẩn bị

### 0.1 Công cụ

| Công cụ | Ghi chú |
|---|---|
| Claude Code CLI | `npm install -g @anthropic-ai/claude-code`, chạy `claude` trong thư mục project |
| JDK 21, Android Studio (+ Kotlin Multiplatform plugin) | Android SDK, emulator |
| **macOS + Xcode** (để build/chạy iOS) | Không có Mac vẫn làm được phần chung + Android; iOS kiểm qua CI macOS |
| Docker Desktop | Chạy backend local |
| Git + GitHub (tuỳ chọn `gh` CLI) | Mỗi đợt một nhánh + PR |

### 0.2 Thư mục làm việc

```bash
mkdir -p ~/projects && cd ~/projects
git clone https://github.com/rockman1510/financemanage_backend.git
git clone https://github.com/rockman1510/finance-management-mobile.git
cd finance-management-mobile
mkdir -p docs/backend
cp ../financemanage_backend/docs/MOBILE_API_GUIDE.md docs/backend/
cp ../financemanage_backend/docs/api/openapi.json docs/backend/
cp ../financemanage_backend/docs/KMP_CLAUDE_CODE_WORKFLOW.md docs/backend/
```

### 0.3 Chạy backend local (giữ chạy trong lúc làm app)

```bash
cd ~/projects/financemanage_backend
docker compose up --build        # http://localhost:8080 ; Android emulator gọi http://10.0.2.2:8080
```

Muốn lấy mã xác thực email khi test: chạy backend profile `dev` thay cho Docker
(`cd backend && ./gradlew :app:bootRun --args='--spring.profiles.active=dev'`) rồi mở
`http://localhost:8080/api/v1/dev/mailbox?to=<email>`.

### 0.4 Cho phép sẵn các lệnh hay dùng (bớt hỏi quyền)

Tạo `finance-management-mobile/.claude/settings.json`:

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew:*)",
      "Bash(git status:*)", "Bash(git diff:*)", "Bash(git log:*)", "Bash(git add:*)", "Bash(git commit:*)",
      "Bash(git checkout:*)", "Bash(git branch:*)",
      "Bash(xcodebuild:*)", "Bash(xcrun simctl:*)",
      "Bash(adb:*)", "Bash(curl:*)", "Bash(jq:*)"
    ]
  }
}
```

---

## 1. Cách làm việc với Claude Code

| Việc | Cách |
|---|---|
| Bắt đầu một đợt | `claude` trong thư mục app → dán prompt của đợt |
| Để Claude **lên kế hoạch trước khi sửa code** | Bấm **Shift+Tab** tới chế độ *plan mode*, dán prompt, duyệt kế hoạch rồi cho chạy |
| Đổi sang đợt mới | `/clear` (bắt đầu ngữ cảnh mới – Claude đọc lại `CLAUDE.md` và `docs/PLAN.md`) |
| Phiên dài, ngữ cảnh đầy | `/compact` |
| Tiếp tục phiên trước | `claude --continue` (phiên gần nhất) hoặc `claude --resume` (chọn phiên) |
| Thêm quy ước lâu dài | Sửa `CLAUDE.md` (hoặc bảo Claude: "thêm quy ước … vào CLAUDE.md") |
| Dừng Claude giữa chừng | `Esc` |

**Vòng lặp mỗi đợt** (lặp lại cho Đợt 1–6):

1. `git checkout -b phase-N` → `/clear` → plan mode → dán prompt Đợt N → duyệt kế hoạch.
2. Claude làm, **tự build + chạy test** (quy định trong `CLAUDE.md`).
3. Bạn chạy app trên emulator/simulator với backend thật, ghi lại lỗi → dùng prompt "Sửa lỗi" (mục 4).
4. Prompt "Review" (mục 4) → sửa → commit → PR → merge → đánh dấu đợt xong trong `docs/PLAN.md`.

**Mẹo**: một đợt quá to thì bảo Claude chia nhỏ ("chỉ làm phần ví trước"); luôn yêu cầu "build và chạy test trước
khi báo xong"; khi Claude đoán sai API, chỉ thẳng mục trong `docs/backend/MOBILE_API_GUIDE.md`.

---

## 2. Phiên khởi tạo

### 2.1 Prompt 0 – kế hoạch, quy ước, khung project

Dán vào `claude` (nên dùng plan mode):

```text
Bạn sẽ xây app mobile Finance Manage (quản lý tài chính cá nhân, tiếng Việt) cho Android và iOS bằng
Kotlin Multiplatform + Compose Multiplatform, nối với backend REST đã có.

ĐỌC TRƯỚC: docs/backend/MOBILE_API_GUIDE.md (toàn bộ) và docs/backend/openapi.json (tham chiếu kiểu dữ liệu).

CÔNG NGHỆ: Kotlin Multiplatform, Compose Multiplatform (Material 3), Navigation Compose, Lifecycle ViewModel,
Ktor Client (OkHttp/Darwin) + kotlinx.serialization, kotlinx-coroutines, kotlinx-datetime, Koin,
Room KMP (cache), DataStore (cài đặt), com.ionspin.kotlin:bignum (tiền), Coil 3 (ảnh), Vico hoặc Canvas (biểu đồ),
Firebase Messaging (push, phần platform). Package gốc: com.financemanage.mobile. Dùng phiên bản ổn định mới nhất,
khai báo trong gradle/libs.versions.toml.

KIẾN TRÚC (module Gradle):
- composeApp: điểm vào Android/iOS, điều hướng, theme, DI.
- core/model (Money, Currency, AppError…), core/network (Ktor, auth, lỗi, Idempotency-Key), core/data (Room, DataStore,
  lưu token an toàn: Keystore/Keychain qua expect/actual), core/ui (design system, component dùng chung), core/testing.
- feature/{auth, onboarding, home, wallet, transaction, budget, bill, saving, deposit, loan, investment, report,
  notification, settings}: mỗi feature chia data / domain / presentation; UI theo UDF (State + Event + ViewModel).
- iosApp: project Xcode.

NHIỆM VỤ PHIÊN NÀY:
1. Viết docs/PLAN.md: kiến trúc trên, danh sách màn hình của từng feature kèm endpoint dùng (đối chiếu mục trong
   MOBILE_API_GUIDE.md), và lộ trình 6 đợt:
   Đợt 1 nền tảng + đăng nhập + onboarding + ví + ghi giao dịch + sổ giao dịch;
   Đợt 2 tổng quan + ngân sách + báo cáo cơ bản;
   Đợt 3 hoá đơn + thông báo/push FCM + giao dịch định kỳ + ảnh hoá đơn + chuyển tiền;
   Đợt 4 mục tiêu tiết kiệm + sổ tiết kiệm + vay nợ;
   Đợt 5 đầu tư + tài sản ròng + dòng tiền + xuất CSV + đa tiền tệ;
   Đợt 6 bảo mật (2FA, phiên, sinh trắc học), cache offline, hoàn thiện, phát hành.
   Mỗi đợt có checklist tiêu chí hoàn thành.
2. Viết CLAUDE.md (quy ước để mọi phiên sau tuân theo) với nội dung ở mục "2.2" bên dưới, bổ sung lệnh build/test
   thực tế của project.
3. Dựng khung project chạy được: màn hình trống có bottom navigation 5 tab (Tổng quan, Sổ giao dịch, nút ➕ giữa,
   Kế hoạch, Tài sản), theme sáng/tối, Koin, cấu hình baseUrl theo platform (Android emulator http://10.0.2.2:8080,
   iOS simulator http://localhost:8080, cho phép cleartext/ATS chỉ ở bản debug).
4. Thêm GitHub Actions: build + test Android trên ubuntu-latest, build framework + app iOS trên macos-latest.
5. Build Android, chạy test, (nếu đang chạy trên macOS) build iOS simulator. Báo kết quả.
Không làm tính năng của các đợt sau trong phiên này.
```

### 2.2 Nội dung `CLAUDE.md` (Claude tạo ở Prompt 0, bạn kiểm lại)

```markdown
# Finance Manage – app KMP (Android + iOS)

## Nguồn sự thật
- API: docs/backend/MOBILE_API_GUIDE.md (cách dùng, quy tắc) + docs/backend/openapi.json (kiểu dữ liệu).
  Không đoán endpoint/trường: tra hai file này. Kế hoạch & tiến độ: docs/PLAN.md.

## Lệnh
- Build Android: ./gradlew :composeApp:assembleDebug   – cài lên emulator: ./gradlew :composeApp:installDebug
- Test: ./gradlew allTests (hoặc :module:allTests)       – lint: ./gradlew ktlintCheck detekt (nếu đã cấu hình)
- iOS (macOS): ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64, rồi
  xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build
- Trước khi báo xong một việc: build Android + chạy test liên quan; sửa hết lỗi biên dịch và test đỏ.

## Quy ước bắt buộc
- Tiền: BigDecimal (bignum), KHÔNG dùng Double/Float. Serializer đọc nội dung gốc của số JSON. Hiển thị vi-VN:
  "1.250.000 ₫" (VND không lẻ, USD 2 chữ số). Gửi số tiền đúng số lẻ của tiền tệ.
- Ngày: kotlinx-datetime LocalDate "yyyy-MM-dd"; "hôm nay" theo timeZone trong hồ sơ người dùng.
- JSON: ignoreUnknownKeys = true, explicitNulls = false; enum có giá trị UNKNOWN dự phòng.
- Auth: access token 15 phút, refresh token dùng MỘT lần (Ktor Auth bearer + refreshTokens); refresh lỗi → xoá token,
  về màn đăng nhập. Token lưu Keystore/Keychain.
- Lỗi: mọi response không 2xx → AppError(status, code, message, fieldErrors). UI dịch `code` sang tiếng Việt
  (bảng mã ở mục 3 của API guide), fieldErrors hiện dưới từng ô.
- Idempotency-Key (UUID sinh khi bấm Lưu, giữ cho các lần thử lại) cho POST /transactions, /transfers,
  /loans/{id}/repayments, /loans/{id}/prepayments.
- Thao tác trả 202 / PENDING (hoá đơn, tiết kiệm, đầu tư, sổ tiết kiệm có ví): hiện "đang xử lý", poll đối tượng
  1–2 giây tối đa ~10 giây, rồi làm mới ví.
- UI: UDF (State bất biến + Event + ViewModel), không gọi API trong Composable. Mọi màn có trạng thái loading
  (skeleton) / empty (có nút hành động) / error (có nút thử lại). Chuỗi hiển thị tiếng Việt trong resources.
- Mỗi feature: data (DTO, API, repository) / domain (model, use case) / presentation (ViewModel, screen).
  Feature không phụ thuộc feature khác; dùng chung qua core/*.
- Test: unit test cho repository (Ktor MockEngine với JSON mẫu trong API guide), use case, ViewModel.
- Không commit bí mật (google-services.json, GoogleService-Info.plist chỉ để local hoặc CI secret).
```

---

## 3. Prompt cho từng đợt

> Trước mỗi đợt: `git checkout -b phase-N`, `/clear`, bật plan mode, dán prompt. Sau đợt: chạy thử, review, PR.

### Đợt 1 – Nền tảng, đăng nhập, ví, ghi giao dịch

```text
Làm Đợt 1 theo docs/PLAN.md và CLAUDE.md. Đối chiếu API: MOBILE_API_GUIDE.md mục 2, 3, 4, 5.1–5.3.

1. core/network: HttpClient Ktor dùng chung (baseUrl theo platform, logging ở debug, timeout), plugin Auth bearer
   với refresh token dùng một lần, map lỗi → AppError, helper Idempotency-Key. core/model: Money (BigDecimal +
   Currency) với serializer, định dạng tiền vi-VN. Lưu token an toàn (expect/actual Keystore/Keychain).
2. feature/auth: màn chào; đăng ký (họ tên, email, mật khẩu 8–72); đăng nhập; nếu twoFactorRequired → màn nhập mã
   6 số / mã khôi phục (/auth/2fa/verify); quên mật khẩu + đặt lại (mã email); gửi deviceName. Đăng xuất gọi
   /auth/logout. Nút Google chỉ hiện trên Android (Credential Manager lấy ID token → /auth/google), ẩn trên iOS.
   Banner "Xác thực email" khi emailVerified=false (gửi mã + nhập mã).
3. feature/onboarding (lần đầu sau đăng ký): chọn tiền tệ mặc định (PATCH /me) → tạo ví đầu tiên với số dư hiện có.
4. feature/wallet: danh sách ví dạng thẻ + tổng quy đổi (/wallets/total), tạo/sửa (loại, tiền tệ, số dư ban đầu,
   thẻ tín dụng: hạn mức, ngày sao kê/đến hạn), lưu trữ, xoá (xử lý WALLET_HAS_TRANSACTIONS → gợi ý lưu trữ),
   điều chỉnh số dư.
5. feature/transaction:
   - Màn ghi nhanh (nút ➕): bàn phím số riêng có nút 000 và tự chèn dấu chấm; tab Chi/Thu; lưới danh mục có icon
     theo `code`, danh mục dùng gần đây lên đầu; mặc định ví gần nhất + hôm nay; "Thêm chi tiết": ghi chú, người
     liên quan, ngày, không tính vào báo cáo. Gửi Idempotency-Key.
   - Sổ giao dịch: nhóm theo ngày (tổng thu/chi mỗi ngày), phân trang vô hạn, kéo để làm mới; bộ lọc ví, danh mục,
     loại, khoảng ngày, khoảng tiền, từ khoá; tổng theo bộ lọc (/transactions/summary).
   - Chi tiết / sửa / xoá; giao dịch có `source` hoặc loại chuyển tiền/điều chỉnh/EXTERNAL thì chỉ xem.
   - Quản lý danh mục: thêm/sửa/xoá danh mục và danh mục con (xử lý CATEGORY_IN_USE, CATEGORY_HAS_CHILDREN).
6. Cache Room cho ví, danh mục, trang giao dịch đầu: mở app thấy dữ liệu cũ ngay rồi làm mới.
7. Test: repository auth (refresh token dùng một lần, refresh 401 → đăng xuất), transaction (Idempotency-Key, map
   lỗi INVALID_AMOUNT), định dạng tiền; ViewModel ghi giao dịch.
Build Android + chạy test; nếu đang trên macOS build iOS. Cập nhật checklist Đợt 1 trong docs/PLAN.md.
```

### Đợt 2 – Tổng quan, ngân sách, báo cáo cơ bản

```text
Làm Đợt 2 theo docs/PLAN.md. API: MOBILE_API_GUIDE.md mục 7, 13 (summary, categories), 5.1 (/wallets/total).

1. feature/home (tab Tổng quan): tổng số dư (nút con mắt ẩn số tiền, nhớ lựa chọn), thẻ thu/chi tháng này + % so với
   tháng trước (/reports/summary), 2–3 ngân sách gần chạm ngưỡng, 5 giao dịch gần nhất, số thông báo chưa đọc trên
   biểu tượng chuông (/notifications/unread-count). Các phần tải song song, phần lỗi không làm hỏng cả màn.
2. feature/budget (trong tab Kế hoạch): danh sách với thanh tiến độ đổi màu theo state (ON_TRACK xanh, WARNING vàng,
   EXCEEDED đỏ), dòng "Còn X/ngày trong N ngày"; tạo/sửa (tên, số tiền, kỳ WEEKLY/MONTHLY/QUARTERLY/YEARLY/CUSTOM,
   lặp lại, chọn danh mục nhiều – cha gồm con, một ví hoặc tất cả); xem kỳ khác (?date=); chạm ngân sách → sổ giao
   dịch đã lọc theo danh mục + kỳ.
3. feature/report: chọn kỳ (tháng này/trước, quý, năm, tuỳ chọn; vuốt ngang đổi tháng); thẻ tổng thu, chi, chênh
   lệch, tỷ lệ tiết kiệm, mũi tên % so với kỳ trước; biểu đồ tròn theo danh mục (EXPENSE/INCOME) → chạm để xem danh
   mục con → danh sách giao dịch.
4. Test: ViewModel home (một phần lỗi), budget (màu theo state), report (đổi kỳ).
Build + test. Cập nhật docs/PLAN.md.
```

### Đợt 3 – Hoá đơn, thông báo + push, định kỳ, ảnh hoá đơn, chuyển tiền

```text
Làm Đợt 3 theo docs/PLAN.md. API: MOBILE_API_GUIDE.md mục 2.1, 5.4–5.6, 8, 14, 14.1.

1. feature/bill (tab Kế hoạch): danh sách theo ngày đến hạn (quá hạn đỏ, ≤ remindDaysBefore vàng); tạo/sửa (gợi ý
   mẫu: điện, nước, internet, điện thoại, thuê nhà – map sang danh mục BILLS_*), tạm dừng; "Trả ngay" (202 →
   "đang xử lý", poll tới RECORDED/FAILED, làm mới ví), "Bỏ qua kỳ này", lịch sử thanh toán.
2. Giao dịch định kỳ: công tắc "Lặp lại" trong màn ghi (tần suất, mỗi N kỳ, ngày kết thúc) + màn quản lý (tạm
   dừng, sửa, xoá).
3. Chuyển tiền giữa ví (từ nút ➕): ví nguồn/đích; khác tiền tệ thì hiện tỷ giá và cho nhập số tiền nhận; Idempotency-Key.
4. Ảnh hoá đơn: chụp/chọn ảnh (expect/actual camera + thư viện), nén < 5 MB, upload multipart phần "file"; xem ảnh
   (tải bằng Ktor có Authorization, không đưa URL thẳng), xoá; tối đa 5 ảnh.
5. feature/notification: màn danh sách (đã đọc/chưa đọc, đọc tất cả), chạm → điều hướng theo
   referenceType/referenceId; cài đặt: bật/tắt từng loại, giờ nhắc ghi chép, khung giờ yên lặng.
6. Push FCM: tích hợp Firebase Messaging (Android + iOS qua expect/actual; google-services.json /
   GoogleService-Info.plist để ngoài git, ghi hướng dẫn trong README). Xin quyền thông báo sau khi người dùng tạo
   ngân sách hoặc hoá đơn đầu tiên. Đăng ký token: POST /notifications/devices {token, provider:"FCM", platform,
   deviceName} sau đăng nhập và khi token đổi; DELETE khi đăng xuất. Android: tạo notification channel "default".
   Bấm push (data: type, referenceType, referenceId) → mở đúng màn hình. Khi app ở foreground: làm mới badge.
7. Test: poll trạng thái PENDING, điều hướng từ thông báo, đăng ký token.
Build + test. Cập nhật docs/PLAN.md và README (cách cấu hình Firebase).
```

### Đợt 4 – Mục tiêu tiết kiệm, sổ tiết kiệm, vay nợ

```text
Làm Đợt 4 theo docs/PLAN.md. API: MOBILE_API_GUIDE.md mục 9, 10, 11.

1. feature/saving (tab Kế hoạch): thẻ mục tiêu có vòng tiến độ, hạn chót, gợi ý mỗi tháng; tạo/sửa; Nạp/Rút
   (202 → poll movements); công tắc "Trích tự động" (số tiền, ví, tuần/tháng, ngày bắt đầu) và "Làm tròn chi tiêu"
   (ví, bội số) kèm câu giải thích; hiệu ứng chúc mừng khi đạt 50%/100%; xoá chỉ khi rỗng (GOAL_NOT_EMPTY).
2. feature/deposit (tab Tài sản): form tạo sổ có xem trước lãi khi gõ (debounce, /deposits/preview), chọn ví trích
   tiền; chi tiết: lãi tích luỹ, đếm ngược đáo hạn, chính sách tái tục, lịch sử; "Tất toán" có báo giá trước
   (/settlement-quote) cảnh báo lãi mất khi rút sớm, chọn ví nhận; trạng thái fundingStatus/payoutStatus.
3. feature/loan (tab Tài sản, hai mục "Tôi cho vay"/"Tôi đi vay"): tạo khoản vay có bảng + biểu đồ lịch trả xem
   trước (/loans/preview-schedule); chi tiết: dư nợ, kỳ tới, kỳ quá hạn, danh sách kỳ (PENDING/PARTIAL/PAID/OVERDUE),
   lịch sử trả; Trả kỳ, Trả trước hạn (hiện phí), Đổi lãi suất – đều có Idempotency-Key cho trả tiền;
   xử lý OVERPAYMENT, SETTLE_DUE_INSTALLMENTS_FIRST.
4. Test: tính/hiển thị lịch trả, báo giá tất toán, ViewModel nạp/rút tiết kiệm.
Build + test. Cập nhật docs/PLAN.md.
```

### Đợt 5 – Đầu tư, tài sản ròng, dòng tiền, CSV, đa tiền tệ

```text
Làm Đợt 5 theo docs/PLAN.md. API: MOBILE_API_GUIDE.md mục 6, 12, 13 (cash-flow, net-worth, transactions.csv).

1. feature/investment (tab Tài sản): danh mục đầu tư (/investments/portfolio) – tổng giá trị, lãi/lỗ (xanh/đỏ, %),
   biểu đồ tròn phân bổ; danh sách tài sản; chi tiết: số lượng, giá vốn bình quân, giá hiện tại + nút cập nhật giá,
   lãi chưa/đã thực hiện, lịch sử; form Mua/Bán tự tính số lượng × giá + phí, chọn ví hoặc "Chỉ theo dõi"
   (cashStatus); nhận cổ tức (chọn danh mục thu). Xử lý INSUFFICIENT_QUANTITY, STALE_PRICE.
2. Tài sản ròng (đầu tab Tài sản + màn chi tiết): số lớn, các nhóm WALLET/SAVING/INVESTMENT/DEPOSIT/LENT/BORROWED.
3. Báo cáo dòng tiền: biểu đồ cột theo tháng/ngày (thu, chi, tiền chuyển sang tiết kiệm/đầu tư) trong feature/report.
4. Xuất CSV: tải /reports/transactions.csv theo kỳ, lưu file, mở share sheet (expect/actual).
5. Đa tiền tệ: màn tỷ giá (danh sách, thêm, xoá, công cụ quy đổi); chọn tiền tệ báo cáo; xử lý
   EXCHANGE_RATE_MISSING bằng lời nhắc "Thêm tỷ giá" dẫn tới màn tỷ giá.
6. Test: định dạng % và lãi/lỗ, ViewModel portfolio, lưu CSV.
Build + test. Cập nhật docs/PLAN.md.
```

### Đợt 6 – Bảo mật, offline, hoàn thiện, phát hành

```text
Làm Đợt 6 theo docs/PLAN.md. API: MOBILE_API_GUIDE.md mục 4.2.

1. feature/settings: hồ sơ (họ tên, tiền tệ mặc định, ngôn ngữ, múi giờ, ngày bắt đầu tháng), đổi/đặt mật khẩu,
   2FA (setup → hiện QR từ otpauthUri + secret → nhập mã → hiện 10 mã khôi phục một lần, cho sao chép; tắt 2FA),
   danh sách thiết bị đăng nhập (thu hồi từng cái / tất cả thiết bị khác), xoá tài khoản (nhập lại email + mật khẩu).
2. Khoá app bằng sinh trắc học (BiometricPrompt / LocalAuthentication qua expect/actual) khi mở lại sau N phút.
3. Offline: cache Room cho mọi danh sách chính; banner mất mạng; hàng đợi giao dịch ghi khi offline gửi lại khi có
   mạng, dùng lại Idempotency-Key đã lưu để không ghi trùng.
4. Hoàn thiện: chế độ tối, cỡ chữ động, accessibility (contentDescription), skeleton/empty/error cho mọi màn, hiệu
   năng danh sách dài, chuỗi tiếng Anh (tuỳ chọn).
5. Phát hành: icon, splash, tên app; cấu hình baseUrl production (https://<DOMAIN>) theo build type; ký app Android,
   cấu hình signing iOS; workflow CI tạo AAB và bản TestFlight (Fastlane hoặc xcodebuild); viết docs/RELEASE.md.
6. Rà toàn bộ checklist mục 6 của docs/backend/KMP_CLAUDE_CODE_WORKFLOW.md và sửa phần còn thiếu.
Build + test cả Android và iOS. Cập nhật docs/PLAN.md.
```

---

## 4. Prompt dùng lại

**Review cuối mỗi đợt**

```text
Review toàn bộ thay đổi của nhánh này so với main như một reviewer khó tính: đúng API theo
docs/backend/MOBILE_API_GUIDE.md, tuân thủ CLAUDE.md (tiền BigDecimal, Idempotency-Key, PENDING, lỗi), rò rỉ
coroutine, xử lý mất mạng, trạng thái loading/empty/error, test còn thiếu. Liệt kê vấn đề theo mức độ, rồi sửa các
vấn đề nghiêm trọng và trung bình, build + chạy test lại.
```

**Sửa lỗi khi chạy thử**

```text
Lỗi khi chạy trên <Android emulator / iOS simulator>: <mô tả, các bước tái hiện, log/stack trace, ảnh chụp nếu có>.
Tìm nguyên nhân gốc (kiểm tra request/response thật nếu liên quan API), sửa, thêm test tái hiện lỗi, build + test.
```

**Kiểm tra với backend thật**

```text
Backend đang chạy ở http://localhost:8080 (Android: 10.0.2.2). Dùng curl theo docs/backend/MOBILE_API_GUIDE.md để
tạo dữ liệu mẫu cho một người dùng test (ví, giao dịch 2 tháng, ngân sách, hoá đơn, mục tiêu, sổ tiết kiệm, khoản
vay, tài sản đầu tư), in email/mật khẩu để tôi đăng nhập app và kiểm tra các màn hình.
```

**Backend đổi API**

```text
Tôi vừa chép docs/backend/MOBILE_API_GUIDE.md và openapi.json mới từ backend. So sánh với bản trước (git diff),
liệt kê thay đổi ảnh hưởng tới app, cập nhật DTO/repository/màn hình tương ứng, build + test.
```

**Thêm một màn hình nhỏ**

```text
Thêm màn <tên> trong feature/<x>: <mô tả>. Endpoint: <mục trong MOBILE_API_GUIDE.md>. Theo đúng cấu trúc
data/domain/presentation và quy ước CLAUDE.md, có test ViewModel. Build + test.
```

**Lệnh tắt riêng (tuỳ chọn)** – tạo `.claude/commands/next-phase.md` trong repo app:

```markdown
Đọc docs/PLAN.md, tìm đợt đầu tiên chưa hoàn thành, đọc prompt tương ứng trong mục 3 của
docs/backend/KMP_CLAUDE_CODE_WORKFLOW.md (nếu có) và làm đợt đó theo CLAUDE.md. $ARGUMENTS
```

rồi trong `claude` gõ `/next-phase` (có thể kèm ghi chú, ví dụ
`/next-phase chỉ làm phần ví`).

---

## 5. Khi cần sửa backend

Mở **một phiên Claude Code khác** trong thư mục `financemanage_backend` (không sửa backend từ phiên app), ví dụ:

```text
App mobile cần <mô tả>, ví dụ endpoint GET /api/v1/home trả trong một request: tổng ví, thu/chi tháng này, 3 ngân
sách gần ngưỡng, 5 giao dịch gần nhất, số thông báo chưa đọc, hoá đơn 7 ngày tới. Làm theo kiến trúc modular
monolith hiện có (module reporting gọi query API của các module), thêm test integration, cập nhật
docs/MOBILE_API_GUIDE.md và xuất lại docs/api/openapi.json, chạy ./gradlew build.
```

Sau khi merge: chép lại `MOBILE_API_GUIDE.md` + `openapi.json` sang `docs/backend/` của app và dùng prompt
"Backend đổi API".

Các việc backend đã biết có thể cần: `GET /api/v1/home` (màn Tổng quan), SSE `/api/v1/events` (dữ liệu live),
`POST /auth/apple` (chỉ khi bật đăng nhập Google trên iOS).

---

## 6. Checklist trước khi phát hành

- [ ] Mọi tính năng trong `docs/PLAN.md` đã đánh dấu xong, test xanh trên CI (Android + iOS).
- [ ] Tiền không dùng Double ở bất kỳ đâu (`grep -rn "Double" feature core`), định dạng vi-VN đúng.
- [ ] Refresh token: hết hạn / dùng lại → về màn đăng nhập, không vòng lặp.
- [ ] Ghi giao dịch khi mạng chập chờn không bị trùng (Idempotency-Key).
- [ ] Thao tác PENDING hiện trạng thái đúng và tự cập nhật.
- [ ] Push: nhận được trên Android và iOS thật (Firebase + APNs key), bấm mở đúng màn, đăng xuất thì hết nhận.
- [ ] Màn nào cũng có loading/empty/error; mất mạng có banner; chế độ tối không lỗi màu.
- [ ] Không có bí mật trong git (google-services.json, plist, keystore); baseUrl production là HTTPS.
- [ ] iOS: không có nút đăng nhập Google (hoặc đã có Sign in with Apple), có màn xoá tài khoản (App Store yêu cầu).
- [ ] Chính sách quyền riêng tư, mô tả quyền camera/thông báo/Face ID trong Info.plist và AndroidManifest.
