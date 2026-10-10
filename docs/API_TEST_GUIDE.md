# Hướng dẫn kiểm chứng business flow qua API

Tài liệu này giúp bạn tự kiểm tra backend có làm đúng nghiệp vụ hay không. Mọi con số kỳ vọng bên dưới đều có công
thức, bạn tự tính lại được bằng máy tính hoặc Excel.

Có 3 cách, từ nhanh đến chi tiết:

1. **Chạy script tự động**: `scripts/api-flow-test.sh` gồm 100 kiểm tra, mỗi dòng in ✔ hoặc ✘.
2. **Gọi tay từng bước bằng curl** (mục 3 trở đi): copy từng lệnh, so kết quả với giá trị kỳ vọng.
3. **Swagger UI**: `http://localhost:8080/swagger-ui.html`. Bấm *Authorize* và dán access token rồi thử từng endpoint.

---

## 1. Khởi động backend

```bash
cd backend
rm -f build/devdb*                                    # xoá DB dev cũ để dữ liệu sạch
./gradlew :app:bootJar
java -jar app/build/libs/app-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  "--app.deposit.maturity-cron=*/10 * * * * *" \
  "--app.loan.reminder-cron=*/10 * * * * *"
```

- Profile `dev` dùng H2 nên không cần Docker. Muốn dùng PostgreSQL thì chạy `docker compose up --build` ở thư mục gốc.
- Hai tham số `cron` cho job đáo hạn/tái tục và job nhắc nợ chạy mỗi 10 giây thay vì mỗi ngày lúc 00:05/00:15, để
  bạn thấy kết quả ngay. Chỉ cần khi test mục 8 (job).
- Kiểm tra app đã chạy: `curl localhost:8080/actuator/health` phải trả `{"status":"UP",...}`.

## 2. Chạy script tự động

```bash
RUN_JOB_TESTS=1 ./scripts/api-flow-test.sh            # cần bash, curl, jq
# BASE_URL=http://host:8080 ./scripts/api-flow-test.sh  # nếu backend ở máy khác
```

Kết quả mong đợi ở dòng cuối: `TẤT CẢ 100 KIỂM TRA ĐỀU ĐẠT`. Không đặt `RUN_JOB_TESTS=1` thì mục 10 (job) được bỏ
qua. Mỗi lần chạy, script tạo người dùng mới nên chạy lại bao nhiêu lần cũng được.

---

## 3. Chuẩn bị cho phần gọi tay

```bash
API=http://localhost:8080/api/v1
EMAIL="me$(date +%s)@example.com"

# Đăng ký → nhận access token (15 phút) + refresh token
curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"secret123\",\"fullName\":\"Nguyễn Văn A\"}" | tee /tmp/auth.json | jq

TOKEN=$(jq -r .accessToken /tmp/auth.json)
AUTH="Authorization: Bearer $TOKEN"
JSON='Content-Type: application/json'
```

> Access token hết hạn sau 15 phút. Khi đó gọi lại `POST /auth/login` và đặt lại biến `TOKEN`.

### 3.1 Xác thực

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| A1 | Đăng ký trùng email (viết HOA) | `curl -s -X POST $API/auth/register -H "$JSON" -d "{\"email\":\"${EMAIL^^}\",\"password\":\"secret123\",\"fullName\":\"X\"}"` | `422`, `code=EMAIL_TAKEN` (email không phân biệt hoa thường) |
| A2 | Sai mật khẩu | `curl -si -X POST $API/auth/login -H "$JSON" -d "{\"email\":\"$EMAIL\",\"password\":\"wrong\"}"` | `401` |
| A3 | Refresh token | `curl -s -X POST $API/auth/refresh -H "$JSON" -d "{\"refreshToken\":\"$(jq -r .refreshToken /tmp/auth.json)\"}"` | `200`, cặp token mới |
| A4 | Dùng lại refresh token cũ (chạy lại A3) | như A3 | `401`, vì token được xoay vòng và chỉ dùng được 1 lần |
| A5 | Gọi API không có token | `curl -si $API/deposits` | `401` |
| A6 | Thông tin tài khoản | `curl -s $API/me -H "$AUTH"` | đúng email và họ tên |

---

## 4. Tiền gửi

### Công thức

| Cách tính (`calculationMethod`) | Công thức |
|---|---|
| `SIMPLE_ACTUAL_365` (mặc định) | gốc × lãi suất × số ngày thực tế / 365 |
| `SIMPLE_30_360` | gốc × lãi suất × số ngày (mỗi tháng 30 ngày) / 360 |
| `COMPOUND_MONTHLY` | gốc × ((1 + lãi suất/12)^số tháng − 1) |

- Rút trước hạn: tiền lãi tính theo `earlyWithdrawalRate` thay cho lãi suất của sổ.
- Mọi số tiền VND được làm tròn đến đồng (làm tròn nửa lên).

### 4.1 Xem trước (không lưu)

```bash
DEP='{"institutionName":"Vietcombank","accountNumber":"VCB-001","type":"TERM","principal":100000000,
      "annualRate":6,"earlyWithdrawalRate":0.5,"termMonths":6,"openDate":"2025-01-01","rolloverPolicy":"NONE"}'
curl -s -X POST $API/deposits/preview -H "$AUTH" -H "$JSON" -d "$DEP" | jq
```

| Trường | Kỳ vọng | Cách tính |
|---|---|---|
| `maturityDate` | `2025-07-01` | 01/01/2025 + 6 tháng |
| `expectedInterest` | `2975342` | 100.000.000 × 6% × 181/365 = 2.975.342,47. Từ 01/01 đến 01/07 có 181 ngày |
| `totalAtMaturity` | `102975342` | gốc + lãi |

Thử đổi tham số:

| Thay đổi | `expectedInterest` kỳ vọng | Cách tính |
|---|---|---|
| `"calculationMethod":"SIMPLE_30_360"` | `3000000` | 100tr × 6% × 180/360 |
| `"annualRate":12,"termMonths":12,"calculationMethod":"COMPOUND_MONTHLY"` | `12682503` | 100tr × (1,01¹² − 1) |
| `"type":"DEMAND","principal":10000000,"annualRate":0.5` (bỏ `termMonths`) | `50000` và `maturityDate=null` | Sổ không kỳ hạn hiển thị lãi của 1 năm: 10tr × 0,5% |

### 4.2 Vòng đời sổ tiết kiệm

```bash
DEP_ID=$(curl -s -X POST $API/deposits -H "$AUTH" -H "$JSON" -d "$DEP" | tee /dev/stderr | jq -r .id)
```

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| D1 | Mở sổ | (lệnh trên) | `201`. `status=ACTIVE`, `transactions[0].type=OPEN`. `accruedInterest=2975342`: sổ mở năm 2025 nên tính đến hôm nay đã quá hạn, lãi tích luỹ dừng ở ngày đáo hạn và không tăng thêm |
| D2 | Lọc danh sách | `curl -s "$API/deposits?status=ACTIVE" -H "$AUTH" \| jq length` | `1`. Với `status=SETTLED` thì là `0` |
| D3 | Sửa thông tin | `curl -s -X PATCH $API/deposits/$DEP_ID -H "$AUTH" -H "$JSON" -d '{"institutionName":"BIDV","note":"Quỹ khẩn cấp"}' \| jq .institutionName` | `"BIDV"` |
| D4 | Báo giá rút trước hạn | `curl -s "$API/deposits/$DEP_ID/settlement-quote?date=2025-04-01" -H "$AUTH" \| jq` | `early=true`. `interest=123288`, tức 100tr × **0,5%** × 90/365. `interestLostByEarlyWithdrawal=1356164`, tức 1.479.452 (lãi 6% của 90 ngày) − 123.288. `total=100123288` |
| D5 | Báo giá đúng ngày đáo hạn | `...settlement-quote?date=2025-07-01` | `early=false`, `interest=2975342` |
| D6 | Báo giá trước ngày mở sổ | `...settlement-quote?date=2024-12-31` | `422` |
| D7 | Tất toán | `curl -s -X POST $API/deposits/$DEP_ID/settle -H "$AUTH" -H "$JSON" -d '{"date":"2025-04-01"}' \| jq` | `200`, cùng số liệu với D4. Không gửi `date` thì lấy ngày hôm nay |
| D8 | Xem lại sổ | `curl -s $API/deposits/$DEP_ID -H "$AUTH" \| jq '{status,settledAmount,settledOn,accruedInterest,tx:[.transactions[].type]}'` | `SETTLED`, `100123288`, `2025-04-01`, `0`, `["OPEN","SETTLE"]` |
| D9 | Tất toán lần 2 | lặp lại D7 | `422`, `code=DEPOSIT_ALREADY_SETTLED` |
| D10 | Thiếu kỳ hạn | `curl -s -X POST $API/deposits -H "$AUTH" -H "$JSON" -d '{"institutionName":"X","type":"TERM","principal":1000,"annualRate":6,"openDate":"2025-01-01"}'` | `422`, `code=INVALID_TERM` |
| D11 | Dữ liệu sai định dạng | `-d '{"type":"TERM","principal":-5}'` | `400`, `code=VALIDATION_FAILED`, `fieldErrors` có `principal` và `institutionName` |
| D12 | Người khác xem sổ | Đăng ký user thứ 2 (mục 3) rồi gọi `GET $API/deposits/$DEP_ID` bằng token của user đó | `404`, vì không được thấy dữ liệu của người khác |
| D13 | Xoá sổ | `curl -si -X DELETE $API/deposits/$DEP_ID -H "$AUTH"` | `204`. Gọi GET lại thì nhận `404` |

---

## 5. Vay nợ

### Quy tắc

- Lãi mỗi kỳ = **dư nợ đầu kỳ × lãi suất năm / 12**. Kỳ k đến hạn vào ngày giải ngân + k tháng.
- Phần gốc mỗi kỳ tuỳ phương thức:
  - `EQUAL_PRINCIPAL`: gốc chia đều.
  - `ANNUITY`: tổng gốc + lãi mỗi kỳ bằng nhau, = P·r / (1 − (1+r)^−n).
  - `BULLET`: chỉ trả lãi, gốc trả hết ở kỳ cuối.
  - `INTEREST_FREE`: gốc chia đều, không lãi.
- Kỳ cuối luôn trả nốt phần dư, nên tổng gốc của lịch luôn đúng bằng số tiền vay.
- Khi thanh toán, tiền được trừ vào **kỳ cũ nhất chưa trả**, **trừ lãi trước rồi mới đến gốc**.
- Trạng thái kỳ: `PAID` (đã trả đủ), `PARTIAL` (trả một phần), `OVERDUE` (quá hạn mà chưa trả đủ), `PENDING` (chưa đến hạn).

### 5.1 Xem trước lịch trả nợ

```bash
LOAN='{"counterpartyName":"VPBank","direction":"BORROW","type":"UNSECURED","principal":120000000,
       "annualRate":12,"termMonths":12,"startDate":"2025-01-15","repaymentMethod":"EQUAL_PRINCIPAL","earlyRepaymentFeeRate":1}'
curl -s -X POST $API/loans/preview-schedule -H "$AUTH" -H "$JSON" -d "$LOAN" \
  | jq '{totalInterest, first: .installments[0], last: .installments[11]}'
```

| Phương thức | Dữ liệu vào | Kỳ vọng |
|---|---|---|
| Gốc đều | `$LOAN` ở trên | Kỳ 1: hạn `2025-02-15`, gốc `10000000`, lãi `1200000` (120tr × 1%). Kỳ 12: lãi `100000`. `totalInterest=7800000`, tức 1% × (120+110+…+10)tr |
| Trả đều | `principal=100000000`, `ANNUITY` | Mọi kỳ có `totalDue ≈ 8884879`, chênh lệch ≤ 5đ do làm tròn. Tổng gốc = `100000000` |
| Gốc cuối kỳ | `principal=12000000`, `termMonths=3`, `BULLET` | Gốc từng kỳ: `0, 0, 12000000`. Lãi mỗi kỳ `120000` |
| Không lãi | `principal=10000000`, `termMonths=3`, `annualRate=10`, `INTEREST_FREE` | Gốc: `3333333, 3333333, 3333334`, kỳ cuối nhận phần lẻ. `totalInterest=0`, lãi suất truyền vào bị bỏ qua |

### 5.2 Thanh toán, trả trước hạn, đổi lãi suất, tất toán

```bash
LOAN_ID=$(curl -s -X POST $API/loans -H "$AUTH" -H "$JSON" -d "$LOAN" | jq -r .summary.id)
show() { curl -s $API/loans/$LOAN_ID -H "$AUTH" | jq "$1"; }
```

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| L1 | Tạo khoản vay | (lệnh trên), sau đó `show .summary` | `outstandingPrincipal=120000000`. `overdueInstallments=12`, vì cả 12 kỳ đều đến hạn trước hôm nay mà chưa trả |
| L2 | Trả kỳ 1 | `curl -s -X POST $API/loans/$LOAN_ID/repayments -H "$AUTH" -H "$JSON" -H 'Idempotency-Key: pay-1' -d '{"amount":11200000,"paidOn":"2025-02-15"}' \| jq` | `201`. `interestPortion=1200000`, `principalPortion=10000000` |
| L3 | Gửi lại **cùng** `Idempotency-Key` | lặp lại L2 | Trả về đúng `id` cũ, không trừ tiền lần 2. `show '.repayments\|length'` → `1`, dư nợ `110000000` |
| L4 | Trả vượt số nợ | `-d '{"amount":999999999,"paidOn":"2025-02-15"}'` | `422`, `code=OVERPAYMENT` |
| L5 | Trả trước hạn 55tr | `curl -s -X POST $API/loans/$LOAN_ID/prepayments -H "$AUTH" -H "$JSON" -d '{"principal":55000000,"paidOn":"2025-02-20"}' \| jq` | `feePortion=550000` (1% của 55tr). `amount=55550000` (gốc + phí) |
| L6 | Lịch sau khi trả trước | `show '{o:.summary.outstandingPrincipal, n:(.installments\|length), k2:.installments[1]}'` | Dư nợ `55000000`. Vẫn **12 kỳ**: số kỳ giữ nguyên, tiền mỗi kỳ giảm. Kỳ 2: gốc `5000000` (55tr / 11 kỳ còn lại), lãi `550000` (55tr × 1%) |
| L7 | Đổi lãi suất lên 24%/năm từ 15/07/2025 | `curl -s -X POST $API/loans/$LOAN_ID/rate-changes -H "$AUTH" -H "$JSON" -d '{"effectiveFrom":"2025-07-15","annualRate":24}' \| jq '[.installments[5,6].interestDue]'` | `[350000, 600000]`. Kỳ 6 bắt đầu 15/06 (trước ngày đổi) nên giữ 1%/tháng × 35tr. Kỳ 7 bắt đầu 15/07 nên áp dụng 2%/tháng × 30tr. `ratePeriods` có 2 dòng |
| L8 | Trả một phần 500k | `-d '{"amount":500000,"paidOn":"2025-03-01"}'` vào `/repayments` | `interestPortion=500000`, `principalPortion=0`: tiền vào lãi kỳ 2 trước |
| L9 | Trả trước khi còn kỳ trả dở | `/prepayments` với `{"principal":1000000,"paidOn":"2025-03-02"}` | `422`, `code=SETTLE_DUE_INSTALLMENTS_FIRST` |
| L10 | Trả hết số còn lại | `REM=$(show '[.installments[].remaining]\|add')`, sau đó POST `/repayments` với `{"amount":'$REM',"paidOn":"2025-03-15"}` | `REM=58850000`, tức 55tr gốc + 4,35tr lãi còn lại − 500k đã trả. Kết quả: `status=PAID_OFF`, `outstandingPrincipal=0` |
| L11 | Trả thêm sau khi đã tất toán | POST `/repayments` bất kỳ | `422`, `code=LOAN_NOT_ACTIVE` |
| L12 | Lọc danh sách | `curl -s "$API/loans?status=PAID_OFF" -H "$AUTH" \| jq length`; thử thêm `?direction=LEND` | `1`; `0` |

Kiểm tra con số 4,35tr ở L10. Sau L5 và L7, gốc mỗi kỳ là 5tr:

- Kỳ 2–6, lãi 1%/tháng, dư nợ đầu kỳ lần lượt 55, 50, 45, 40, 35tr → tổng lãi 2,25tr.
- Kỳ 7–12, lãi 2%/tháng, dư nợ đầu kỳ lần lượt 30, 25, 20, 15, 10, 5tr → tổng lãi 2,1tr.
- Cộng lại 4,35tr.

---

## 6. Dashboard

Dùng **một user mới** để số liệu không lẫn với các bước trên. Ngày tính theo giờ Việt Nam.

```bash
OPEN=$(date -d '-15 days' +%F)          # macOS: date -v-15d +%F
START=$(date -d '-10 days' +%F)
curl -s -X POST $API/deposits -H "$AUTH" -H "$JSON" -d "{\"institutionName\":\"ACB\",\"type\":\"TERM\",\"principal\":50000000,\"annualRate\":5,\"termMonths\":1,\"openDate\":\"$OPEN\"}" >/dev/null
curl -s $API/dashboard -H "$AUTH" | jq
curl -s -X POST $API/loans -H "$AUTH" -H "$JSON" -d "{\"counterpartyName\":\"Bình\",\"direction\":\"BORROW\",\"principal\":20000000,\"termMonths\":1,\"startDate\":\"$START\",\"repaymentMethod\":\"INTEREST_FREE\"}" >/dev/null
curl -s -X POST $API/loans -H "$AUTH" -H "$JSON" -d "{\"counterpartyName\":\"An\",\"direction\":\"LEND\",\"principal\":5000000,\"termMonths\":1,\"startDate\":\"$START\",\"repaymentMethod\":\"INTEREST_FREE\"}" >/dev/null
curl -s $API/dashboard -H "$AUTH" | jq
```

| Trường | Kỳ vọng |
|---|---|
| `totals[0].deposits` | `50000000` |
| `totals[0].accruedInterest` | `102740` = 50tr × 5% × 15/365 |
| `totals[0].borrowedOutstanding` / `lentOutstanding` | `20000000` / `5000000` |
| `totals[0].netWorth` | **tiền gửi + lãi tích luỹ + cho vay − đi vay** = 50.000.000 + 102.740 + 5.000.000 − 20.000.000 = `35102740` |
| `activeDeposits` / `activeLoans` | `1` / `2`. Số liệu cập nhật ngay sau khi thêm khoản vay, tức cache đã được làm mới |
| `upcoming` (30 ngày tới) | 3 dòng: sổ ACB đáo hạn (`IN`, gốc + lãi), thu nợ An (`IN`, 5tr), trả nợ Bình (`OUT`, 20tr) |
| `monthlyCashFlow` | Dòng tiền dự kiến 12 tháng, gộp theo tháng: `inflow`, `outflow`, `net` |

---

## 7. Job đáo hạn, tái tục và thông báo

Phần này cần app đang chạy với cron mỗi 10 giây (mục 1). Tạo các sổ kỳ hạn 2 tháng đã đáo hạn cách đây 3 ngày:

```bash
OPENED=$(date -d "$(date -d '-3 days' +%F) -2 months" +%F)
for p in NONE PRINCIPAL_AND_INTEREST PRINCIPAL_ONLY; do
  curl -s -X POST $API/deposits -H "$AUTH" -H "$JSON" \
    -d "{\"institutionName\":\"$p\",\"type\":\"TERM\",\"principal\":100000000,\"annualRate\":6,\"termMonths\":2,\"openDate\":\"$OPENED\",\"rolloverPolicy\":\"$p\"}" \
    | jq '{institutionName, expectedInterestAtMaturity, maturityDate}'
done
sleep 15
curl -s "$API/deposits" -H "$AUTH" | jq '.[] | {institutionName, status, principal, openDate, maturityDate, tx:[.transactions[].type]}'
```

| Khi đáo hạn (`rolloverPolicy`) | Kỳ vọng sau khi job chạy |
|---|---|
| `NONE` | `status=MATURED`. Sổ chờ người dùng tất toán |
| `PRINCIPAL_AND_INTEREST` | Vẫn `ACTIVE`. `principal` = 100tr + `expectedInterestAtMaturity` (in ra lúc tạo sổ). `openDate` = ngày đáo hạn cũ, `maturityDate` lùi thêm 2 tháng. Giao dịch: `OPEN, ROLLOVER` |
| `PRINCIPAL_ONLY` | Vẫn `ACTIVE`, `principal` giữ 100tr. Giao dịch: `OPEN, INTEREST_PAID, ROLLOVER` |

Nếu job không chạy nhiều ngày rồi mới chạy lại, sổ được tái tục nhiều lần liên tiếp cho đến kỳ hiện tại.

Thông báo xem bằng `curl -s $API/notifications -H "$AUTH" | jq '.[] | {type,title,body}'`:

| Loại | Điều kiện tạo |
|---|---|
| `DEPOSIT_MATURED` | Sổ đáo hạn hoặc vừa được tái tục |
| `DEPOSIT_MATURING_SOON` | Sổ đáo hạn đúng **7 ngày** sau hôm nay (`app.deposit.reminder-days-before`) |
| `INSTALLMENT_DUE_SOON` | Có kỳ trả nợ đến hạn đúng **3 ngày** sau hôm nay (`app.loan.reminder-days-before`) |
| `INSTALLMENT_OVERDUE` | Có kỳ đến hạn **hôm qua** mà chưa trả đủ |
| `LOAN_PAID_OFF` | Khoản vay vừa được trả hết |

Đánh dấu đã đọc: `POST $API/notifications/{id}/read` → `204`. Xem số chưa đọc: `GET $API/notifications/unread-count`.

> **Hạn chế đã biết:** job nhắc nhở không chống trùng. Nếu job chạy nhiều lần trong cùng một ngày (như cron 10 giây
> ở đây, hoặc khi khởi động lại app rồi job chạy thêm lần nữa), các thông báo `*_SOON` và `INSTALLMENT_OVERDUE`
> sẽ bị lặp. Với cron mặc định mỗi ngày 1 lần thì không xảy ra.

---

## 8. Bảng đối chiếu quy tắc nghiệp vụ

| Quy tắc | Kiểm tra ở |
|---|---|
| Ngày đáo hạn = ngày gửi + kỳ hạn; sổ không kỳ hạn không có ngày đáo hạn | 4.1 |
| 3 cách tính lãi cho kết quả đúng công thức | 4.1 |
| Lãi tích luỹ dừng ở ngày đáo hạn | D1 |
| Rút trước hạn tính lãi theo lãi suất không kỳ hạn, báo rõ số lãi bị mất | D4 |
| Không tất toán 2 lần; không tất toán trước ngày gửi | D6, D9 |
| Tái tục gốc / gốc + lãi / không tái tục | Mục 7 |
| Lịch trả nợ đúng cho 4 phương thức, tổng gốc luôn đúng bằng số tiền vay | 5.1 |
| Thanh toán trừ lãi trước gốc, kỳ cũ trước | L2, L8 |
| Gửi lại cùng Idempotency-Key không trừ tiền 2 lần | L3 |
| Không cho trả vượt số nợ | L4 |
| Trả trước hạn: thu phí, tính lại lịch, giữ số kỳ | L5, L6 |
| Phải trả hết kỳ trả dở hoặc quá hạn trước khi trả trước hạn | L9 |
| Lãi thả nổi chỉ áp dụng từ kỳ bắt đầu sau ngày hiệu lực | L7 |
| Trả hết thì khoản vay chuyển sang tất toán và khoá thanh toán | L10, L11 |
| Tài sản ròng = gửi + lãi + cho vay − đi vay; dashboard cập nhật ngay | Mục 6 |
| Người dùng chỉ thấy dữ liệu của mình | D12 |
| Refresh token chỉ dùng được 1 lần | A3, A4 |
