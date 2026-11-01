# Phạm vi tính năng – giai đoạn mở rộng

> Tài liệu chốt phạm vi tính năng mới, tham khảo ứng dụng Money Lover. Bổ sung cho
> [kế hoạch kiến trúc](ARCHITECTURE_PLAN.md).
>
> Quyết định chung:
> - Vẫn giữ **Modular Monolith** (Java 21, Spring Boot) và triển khai trên **một VPS** (phương án A). Không chuyển sang
>   KMP/Kotlin.
> - Phạm vi chỉ gồm **backend**. Những gì thuần phía mobile (PIN, sinh trắc, widget…) không nằm trong tài liệu này.
> - Không làm tính năng nào bắt buộc phải kết nối ngân hàng (open banking, ví liên kết).

## 1. Danh sách tính năng

Mức độ: **MVP** là bản tối thiểu, **Mở rộng** là bản đầy đủ hơn.

| # | Tính năng | Mức độ | Module | Trạng thái |
|---|---|---|---|---|
| 1 | Quản lý ví (tự nhập, không kết nối ngân hàng) | MVP | `ledger` | Xong (GĐ 1) |
| 2 | Ghi chép giao dịch | MVP | `ledger` | Xong (GĐ 1) |
| 3 | Ngân sách | MVP | `budget` | Xong (GĐ 3) |
| 4 | Hoá đơn | MVP | `bill` | Xong (GĐ 3) |
| 5 | Tiết kiệm | **Mở rộng** (bỏ mục tiêu chung gia đình) | `saving` (mới) + `deposit` (đã có) | Một phần (sổ tiết kiệm) |
| 6 | Đầu tư | MVP | `investment` (mới) | Chưa làm |
| 7 | Báo cáo và thống kê | MVP | `reporting` (đã có) | Một phần (dashboard tiền gửi/vay nợ) |
| 8 | Đa tiền tệ | MVP | `currency` | Xong (GĐ 1); nguồn tỷ giá tự động chưa có provider |
| 9 | Đăng ký và đăng nhập | **Mở rộng** (Google, không làm Apple) | `identity` | Xong (GĐ 2) |
| 10 | Hệ thống thông báo | MVP | `notification` | Xong (GĐ 4) |

Vay nợ (`loan`) đã có từ trước và giữ nguyên.

## 2. Chi tiết

### 2.1. Quản lý ví
- Tạo/sửa/xoá (lưu trữ) ví: tên, loại (tiền mặt, tài khoản ngân hàng, ví điện tử, thẻ tín dụng, khác), tiền tệ, số dư
  ban đầu, biểu tượng.
- Số dư được tính từ các giao dịch, có thao tác **điều chỉnh số dư** (sinh ra một giao dịch điều chỉnh).
- **Chuyển tiền giữa các ví**, kể cả khác tiền tệ (ghi tỷ giá áp dụng).
- Tuỳ chọn **không tính ví vào tổng số dư**.
- Thẻ tín dụng: hạn mức, ngày sao kê, ngày đến hạn thanh toán.

### 2.2. Ghi chép giao dịch
- Thu/chi gồm: số tiền, ví, danh mục, ngày, ghi chú, người liên quan, ảnh hoá đơn (lưu file qua port `AttachmentStorage`).
- **Danh mục**: bộ mặc định khi đăng ký, người dùng tự thêm/sửa, hỗ trợ danh mục con.
- **Giao dịch định kỳ**: lịch lặp (ngày/tuần/tháng/năm), job sinh giao dịch đến hạn.
- Tìm kiếm và lọc theo khoảng thời gian, ví, danh mục, số tiền, từ khoá. Phân trang.

### 2.3. Ngân sách
- Ngân sách theo một hoặc **nhiều danh mục**, theo tuần/tháng/quý/năm/khoảng tuỳ chọn, có thể lặp lại theo kỳ.
- Theo dõi đã chi / còn lại và **gợi ý số tiền nên tiêu mỗi ngày**.
- Phát event khi đạt 80% và 100% ngân sách để module thông báo gửi cảnh báo.

### 2.4. Hoá đơn
- Hoá đơn định kỳ: tên, số tiền dự kiến, danh mục, ví mặc định, chu kỳ, ngày đến hạn.
- Đánh dấu đã thanh toán (sinh giao dịch chi trong `ledger`), bỏ qua một kỳ.
- Phát event sắp đến hạn / quá hạn.

### 2.5. Tiết kiệm (Mở rộng)
- **Mục tiêu tiết kiệm**: tên, số tiền cần đạt, hạn chót, biểu tượng. Nạp vào/rút ra từ một ví.
- Tiến độ (%) và **gợi ý số tiền cần để dành mỗi tháng/tuần** để kịp hạn.
- **Tự động trích tiền định kỳ** vào mục tiêu.
- **Làm tròn (round-up)**: nghe event giao dịch chi, chuyển phần làm tròn vào mục tiêu đã chọn.
- **Nối sổ tiết kiệm (`deposit`) với ví**: mở sổ trừ tiền ví, tất toán cộng gốc + lãi vào ví.
- Phát event khi đạt các mốc 50% / 100%.
- **Không làm**: mục tiêu chung cho gia đình (và mọi tính năng chia sẻ dữ liệu giữa nhiều người dùng).

### 2.6. Đầu tư (MVP)
- Tài sản: cổ phiếu, chứng chỉ quỹ, vàng, crypto, bất động sản, khác.
- Giao dịch mua/bán: số lượng, giá, phí, ngày, ví thanh toán.
- Giá vốn bình quân, lãi/lỗ đã chốt và chưa chốt. Cổ tức/coupon ghi nhận như khoản thu.
- Giá hiện tại **nhập tay**, qua port `PriceProvider` để sau này thêm nguồn giá tự động mà không sửa nghiệp vụ.
- Giá trị danh mục được tính vào tài sản ròng.

### 2.7. Báo cáo và thống kê
- Tổng thu, tổng chi, chênh lệch theo kỳ; chi tiêu theo danh mục; so sánh giữa các kỳ.
- Dòng tiền và tài sản ròng (ví + tiết kiệm + tiền gửi + đầu tư + cho vay − đi vay), quy đổi về tiền tệ mặc định.
- Xuất dữ liệu ra CSV.

### 2.8. Đa tiền tệ
- Mỗi ví một tiền tệ; người dùng có tiền tệ mặc định.
- Bảng tỷ giá: người dùng nhập tay, hoặc job lấy từ nguồn ngoài qua port `ExchangeRateProvider`.
- Quy đổi khi xem tổng số dư và báo cáo.

### 2.9. Đăng ký và đăng nhập (Mở rộng)
Đã có: đăng ký/đăng nhập email + mật khẩu, JWT, refresh token xoay vòng, đăng xuất, `/me`.

Bổ sung:
- **Xác thực email** bằng OTP/link; **quên mật khẩu** và đặt lại; **đổi mật khẩu**.
- **Giới hạn đăng nhập sai** (rate limit, tạm khoá).
- **Đăng nhập bằng Google**: backend xác thực Google ID token (miễn phí), tạo hoặc liên kết tài khoản theo email đã
  xác thực.
- **Quản lý thiết bị/phiên** đang đăng nhập, đăng xuất từ xa.
- **2FA (TOTP)**.
- **Hồ sơ người dùng**: tiền tệ mặc định, ngôn ngữ, múi giờ, ngày bắt đầu tháng.
- **Xoá tài khoản**: phát event `UserDeleted`, mỗi module tự xoá dữ liệu của mình.
- Gửi email qua port `EmailSender` (adapter cho nhà cung cấp như Resend/SES/Brevo; adapter log khi chạy local).

Không làm: Sign in with Apple, phần giao diện mobile.

> Lưu ý khi phát hành iOS: theo App Store Review Guideline 4.8, app có đăng nhập bằng bên thứ ba (Google) thường phải
> có thêm một lựa chọn đăng nhập bảo vệ quyền riêng tư tương đương (ví dụ Sign in with Apple). Khi phát hành iOS cần
> xem lại điểm này; backend vẫn thêm được Apple sau qua cùng cơ chế với Google.

### 2.10. Hệ thống thông báo (MVP)
Đã có: thông báo in-app sinh từ event của tiền gửi và vay nợ.

Bổ sung:
- **Push notification qua Expo Push**: lưu/xoá push token theo thiết bị, adapter `NotificationSender` gửi push.
- Loại thông báo mới: ngân sách 80%/100%, hoá đơn sắp đến hạn/quá hạn, mục tiêu tiết kiệm đạt mốc, nhắc ghi chép hằng
  ngày.
- Đánh dấu đã đọc (từng cái / tất cả), đếm số chưa đọc.
- **Cài đặt thông báo**: bật/tắt từng loại, giờ nhắc, khung giờ không làm phiền.

## 3. Lịch trình triển khai

Mỗi giai đoạn là **một pull request** riêng. PR chỉ xong khi: CI xanh, có unit test cho domain, API test (H2 và
PostgreSQL), `ModularityTests` qua, cập nhật `docs/API_TEST_GUIDE.md` + `scripts/api-flow-test.sh`, và cập nhật cột
trạng thái ở mục 1. Giai đoạn sau chỉ bắt đầu khi PR trước đã được review.

Thời gian ước tính tính theo **1 lập trình viên toàn thời gian**, để tham khảo độ lớn công việc.

| GĐ | Nội dung | Phụ thuộc | Ước tính |
|---|---|---|---|
| 1 | Ledger (ví, giao dịch, danh mục, định kỳ) + đa tiền tệ | – | 2–3 tuần |
| 2 | Đăng ký/đăng nhập mở rộng | 1 | 2 tuần |
| 3 | Ngân sách + Hoá đơn | 1 | 1,5–2 tuần |
| 4 | Thông báo MVP | 2, 3 | 1–1,5 tuần |
| 5 | Tiết kiệm (mở rộng) + nối tiền gửi với ví | 1, 4 | 1,5–2 tuần |
| 6 | Đầu tư MVP | 1 | 1,5 tuần |
| 7 | Báo cáo và thống kê | 1–6 | 2 tuần |
| 8 | Hoàn thiện và triển khai lên VPS | 1–7 | 1 tuần |
| | **Tổng** | | **~13–16 tuần** |

### GĐ 1 – Ledger + Đa tiền tệ
- Thêm named interface `identity.api` với event `UserRegistered` (hiện `identity` chưa phát event nào), để `ledger`
  tạo danh mục mặc định cho người dùng mới.
- Module `ledger`, schema `ledger`: bảng `wallet`, `category`, `money_transaction`, `recurring_transaction`,
  `attachment`.
  - API: CRUD ví, điều chỉnh số dư, chuyển tiền giữa ví (kể cả khác tiền tệ); CRUD danh mục (có danh mục con);
    CRUD giao dịch có lọc + phân trang; CRUD giao dịch định kỳ; đính kèm ảnh hoá đơn qua port `AttachmentStorage`
    (adapter lưu đĩa local).
  - Ghi giao dịch dùng Idempotency-Key; số dư ví cập nhật cùng transaction, có optimistic locking.
  - Event trong `ledger.api`: `TransactionRecorded/Updated/Deleted` (mỗi vế chuyển tiền là một giao dịch),
    `WalletChanged/WalletDeleted`.
  - Job `RecurringTransactionJob` (ShedLock).
- Module `currency`, schema `currency`: bảng `exchange_rate`; API nhập/xem tỷ giá; `CurrencyConversionApi` cho module
  khác; port `ExchangeRateProvider` (mặc định chỉ dùng tỷ giá nhập tay) + job làm mới tỷ giá.

### GĐ 2 – Đăng ký/đăng nhập mở rộng
- Port `EmailSender` (adapter log cho local, adapter SMTP/HTTP cho production).
- Xác thực email (OTP), quên mật khẩu + đặt lại, đổi mật khẩu (thu hồi các phiên khác).
- Rate limit + tạm khoá khi đăng nhập sai (bộ nhớ, đổi sang Redis bằng cấu hình).
- Đăng nhập Google: `POST /api/v1/auth/google` nhận ID token, xác thực chữ ký + `aud`, tạo/liên kết tài khoản.
- Quản lý phiên/thiết bị: liệt kê, đăng xuất từ xa (gắn thông tin thiết bị vào refresh token).
- Hồ sơ: tiền tệ mặc định, ngôn ngữ, múi giờ, ngày bắt đầu tháng; phát `UserProfileChanged`.
- 2FA TOTP: bật/tắt, mã khôi phục, bước xác thực thứ hai khi đăng nhập.
- Xoá tài khoản: phát `UserDeleted`; mọi module (cả `deposit`, `loan`, `notification` hiện có) nghe event và tự xoá
  dữ liệu.

Đã làm thêm ở GĐ 2: job dọn token/mã hết hạn; phát hiện dùng lại refresh token cũ (huỷ cả phiên); hộp thư giả
`/api/v1/dev/mailbox` cho profile `dev`. Hạn chế đã biết: access token của thiết bị bị đăng xuất (hoặc của tài khoản
đã xoá) vẫn dùng được tới khi hết hạn (15 phút); chưa giới hạn số request theo IP.

### GĐ 3 – Ngân sách + Hoá đơn
- Module `budget`: ngân sách một/nhiều danh mục, theo kỳ hoặc khoảng tuỳ chọn, lặp lại; đã chi/còn lại/gợi ý mỗi ngày;
  nghe event giao dịch của `ledger`; phát `BudgetThresholdReached` (80%, 100%).
- Module `bill`: hoá đơn định kỳ; thanh toán (gửi lệnh ghi giao dịch sang `ledger` qua event, có bù trừ), bỏ qua kỳ;
  job phát `BillDueSoon` / `BillOverdue`.

### GĐ 4 – Thông báo MVP
- Đăng ký/huỷ push token theo thiết bị; adapter `NotificationSender` gửi qua Expo Push (giữ adapter log cho local).
- Loại thông báo mới: ngân sách, hoá đơn, mục tiêu tiết kiệm, nhắc ghi chép hằng ngày (job theo múi giờ người dùng).
- Đánh dấu đã đọc (từng cái/tất cả), đếm chưa đọc.
- Cài đặt: bật/tắt theo loại, giờ nhắc, khung giờ không làm phiền.

### GĐ 5 – Tiết kiệm (mở rộng)
- Module `saving`: mục tiêu, nạp/rút từ ví theo luồng event + bù trừ với `ledger`; tiến độ và gợi ý mức để dành;
  trích tiền định kỳ (job); round-up khi nghe giao dịch chi; event đạt mốc 50%/100%.
- Nối `deposit` với ví: mở sổ trừ tiền ví, tất toán cộng gốc + lãi vào ví (qua event, tuỳ chọn chọn ví).

### GĐ 6 – Đầu tư MVP
- Module `investment`: tài sản, giao dịch mua/bán (trả/nhận tiền qua ví bằng event), giá vốn bình quân, lãi/lỗ đã
  chốt/chưa chốt, cổ tức/coupon; nhập giá tay qua port `PriceProvider`; `InvestmentQueryApi` cho báo cáo.

### GĐ 7 – Báo cáo và thống kê
- Read model riêng của `reporting`, dựng từ event của `ledger` (không gọi đồng bộ cho dữ liệu thu/chi).
- Báo cáo thu/chi theo kỳ, theo danh mục, so sánh kỳ; dòng tiền; tài sản ròng gồm ví, tiết kiệm, tiền gửi, đầu tư,
  cho vay, đi vay, quy đổi về tiền tệ mặc định; xuất CSV.
- Cache dashboard chuyển sang Redis được bằng cấu hình.

### GĐ 8 – Hoàn thiện và triển khai (phương án A)
- `docker-compose.prod.yml`, Caddy/Nginx + HTTPS, biến môi trường/secret, backup PostgreSQL tự động ra object storage.
- Kiểm tra hiệu năng các truy vấn chính (index), Actuator/Prometheus, hướng dẫn deploy lên VPS trong `docs/`.

## 4. Nguyên tắc để sau này tách thành hệ thống phân tán dễ dàng

1. **Một transaction chỉ ghi dữ liệu của một module.** Luồng chạm nhiều module (nạp tiền vào mục tiêu, mua tài sản bằng
   ví, trả hoá đơn từ ví…) đi qua event có outbox, kèm bước bù trừ nếu bên nhận từ chối.
2. **Event và lệnh ghi đều idempotent** (`eventId`, Idempotency-Key).
3. **Không JOIN hay khoá ngoại sang schema khác**; chỉ lưu id dạng UUID.
4. **`reporting` đọc dữ liệu thu/chi từ read model của chính nó**, dựng từ event, hạn chế gọi đồng bộ sang module khác.
5. **Không giữ trạng thái trong bộ nhớ tiến trình**: cache, rate limit, khoá job phải chuyển sang Redis/ShedLock được
   bằng cấu hình.
6. **Event là hợp đồng công khai**: nằm trong package `api`, chỉ thêm field, không đổi nghĩa field cũ.
7. **Ví và giao dịch luôn nằm chung module `ledger`** để số dư luôn nhất quán.
