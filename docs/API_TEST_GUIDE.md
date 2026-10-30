# Hướng dẫn kiểm chứng business flow qua API

Tài liệu này giúp bạn tự kiểm tra backend có làm đúng nghiệp vụ hay không. Mọi con số kỳ vọng bên dưới đều có công
thức, bạn tự tính lại được bằng máy tính hoặc Excel.

Có 3 cách, từ nhanh đến chi tiết:

1. **Chạy script tự động**: `scripts/api-flow-test.sh` gồm 192 kiểm tra, mỗi dòng in ✔ hoặc ✘.
2. **Gọi tay từng bước bằng curl** (mục 3 trở đi): copy từng lệnh, so kết quả với giá trị kỳ vọng.
3. **Swagger UI**: `http://localhost:8080/swagger-ui.html`. Bấm *Authorize* và dán access token rồi thử từng endpoint.

---

## 1. Khởi động backend

```bash
cd backend
rm -f build/devdb.mv.db build/devdb.trace.db         # xoá DB dev cũ để dữ liệu sạch (không có file cũng không sao)
./gradlew :app:bootJar
java -jar app/build/libs/app-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev \
  "--app.deposit.maturity-cron=*/10 * * * * *" \
  "--app.loan.reminder-cron=*/10 * * * * *"
```

- Profile `dev` dùng H2 nên không cần Docker. Muốn dùng PostgreSQL thì chạy `docker compose up --build` ở thư mục gốc.
- Hai tham số `cron` cho job đáo hạn/tái tục và job nhắc nợ chạy mỗi 10 giây thay vì mỗi ngày lúc 00:05/00:15, để
  bạn thấy kết quả ngay. Chỉ cần khi test mục 13 (job).
- Các lệnh trong tài liệu chạy được trên cả **zsh** (mặc định trên macOS) lẫn bash. Riêng lệnh `date` có khác biệt
  giữa macOS và Linux, nên ở chỗ dùng `date` tài liệu ghi cả hai dạng.
- Kiểm tra app đã chạy: `curl localhost:8080/actuator/health` phải trả `{"status":"UP",...}`.
- Profile `dev` không gửi email thật: mọi email (mã xác thực, mã đặt lại mật khẩu) nằm trong hộp thư giả
  `GET /api/v1/dev/mailbox?to=<email>` (mới nhất trước). Endpoint này không tồn tại ở profile khác.

## 2. Chạy script tự động

```bash
RUN_JOB_TESTS=1 ./scripts/api-flow-test.sh            # cần bash, curl, jq
# BASE_URL=http://host:8080 ./scripts/api-flow-test.sh  # nếu backend ở máy khác
```

Kết quả mong đợi ở dòng cuối: `TẤT CẢ 192 KIỂM TRA ĐỀU ĐẠT`. Không đặt `RUN_JOB_TESTS=1` thì mục 16 (job) được bỏ
qua (còn 183 kiểm tra). Phần mã gửi qua email (mục 15) cần backend chạy profile `dev`. Mỗi lần chạy, script tạo người dùng mới nên chạy lại bao nhiêu lần cũng được.

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
| A1 | Đăng ký trùng email (viết HOA) | `curl -s -X POST $API/auth/register -H "$JSON" -d "{\"email\":\"$(echo $EMAIL \| tr a-z A-Z)\",\"password\":\"secret123\",\"fullName\":\"X\"}"` | `422`, `code=EMAIL_TAKEN` (email không phân biệt hoa thường) |
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
# macOS (zsh/bash mặc định):
OPEN=$(date -v-15d +%F); START=$(date -v-10d +%F)
# Linux:
# OPEN=$(date -d '-15 days' +%F); START=$(date -d '-10 days' +%F)
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

## 7. Ví & giao dịch thu chi

Dùng **một user mới**. Mỗi user mới có sẵn bộ danh mục mặc định (giống Money Lover); danh mục mặc định có `code`
cố định để app dịch tên.

```bash
curl -s "$API/categories" -H "$AUTH" | jq -r '.[] | "\(.code)\t\(.type)\t\(.name)\t\(.parentId // "")"'
FOOD=$(curl -s "$API/categories" -H "$AUTH" | jq -r '.[] | select(.code=="FOOD") | .id')
COFFEE=$(curl -s "$API/categories" -H "$AUTH" | jq -r '.[] | select(.code=="FOOD_COFFEE") | .id')
SALARY=$(curl -s "$API/categories" -H "$AUTH" | jq -r '.[] | select(.code=="SALARY") | .id')

CASH=$(curl -s -X POST $API/wallets -H "$AUTH" -H "$JSON" \
  -d '{"name":"Tiền mặt","type":"CASH","currency":"VND","initialBalance":1000000}' | jq -r .id)
BANK=$(curl -s -X POST $API/wallets -H "$AUTH" -H "$JSON" \
  -d '{"name":"Vietcombank","type":"BANK_ACCOUNT","currency":"VND","initialBalance":5000000}' | jq -r .id)
LUNCH="{\"walletId\":\"$CASH\",\"categoryId\":\"$FOOD\",\"amount\":50000,\"occurredOn\":\"2025-04-01\",\"note\":\"Phở bò\"}"
```

Quy tắc:

- **Số dư ví = số dư ban đầu + tổng các giao dịch** (thu, chuyển vào, điều chỉnh tăng là cộng; chi, chuyển ra,
  điều chỉnh giảm là trừ). Số dư được cập nhật trong cùng database transaction với giao dịch.
- Danh mục quyết định giao dịch là **thu** hay **chi**; số tiền luôn dương, theo tiền tệ của ví.
- Số tiền không được có nhiều số lẻ hơn tiền tệ cho phép: VND không có số lẻ, USD tối đa 2 số lẻ.
- Danh mục có tối đa 2 cấp. Lọc theo danh mục cha thì gồm cả danh mục con.
- Ví đã có giao dịch không xoá được, chỉ **lưu trữ** (`archived=true`); ví lưu trữ không nhận giao dịch mới.

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| W1 | Ghi khoản chi | `curl -s -X POST $API/transactions -H "$AUTH" -H "$JSON" -H 'Idempotency-Key: lunch-1' -d "$LUNCH" \| jq` | `201`, `type=EXPENSE` |
| W2 | Gửi lại W1 (cùng key) | như W1 | Cùng `id`. `GET $API/wallets/$CASH` có `balance=950000`, tức chỉ trừ 1 lần |
| W3 | Thêm chi cà phê và thu lương | `curl -s -X POST $API/transactions -H "$AUTH" -H "$JSON" -d "{\"walletId\":\"$CASH\",\"categoryId\":\"$COFFEE\",\"amount\":30000,\"occurredOn\":\"2025-04-02\"}"`, rồi tương tự với `"walletId":"$BANK","categoryId":"$SALARY","amount":20000000,"occurredOn":"2025-04-05"` | `201` |
| W4 | Lọc theo danh mục cha | `curl -s "$API/transactions?categoryId=$FOOD" -H "$AUTH" \| jq .totalElements` | `2` (Ăn uống + Cà phê) |
| W5 | Lọc ngày + phân trang | `curl -s "$API/transactions?from=2025-04-02&to=2025-04-30&size=1" -H "$AUTH" \| jq '{totalElements,totalPages,n:(.items\|length)}'` | `2`, `2`, `1` |
| W6 | Tổng thu chi | `curl -s "$API/transactions/summary?from=2025-04-01&to=2025-04-30" -H "$AUTH" \| jq` | `income=20000000`, `expense=80000`, `net=19920000` |
| W7 | Chuyển giao dịch sang ví khác, sửa số tiền | `curl -s -X PATCH $API/transactions/<id W1> -H "$AUTH" -H "$JSON" -d "{\"walletId\":\"$BANK\",\"amount\":80000}"` | `200`. Ví tiền mặt `970000` (1tr − 30k), ví ngân hàng `24920000` (5tr + 20tr − 80k) |
| W8 | Xoá giao dịch | `curl -si -X DELETE $API/transactions/<id W1> -H "$AUTH"` | `204`. Ví ngân hàng về `25000000` |
| W9 | Điều chỉnh số dư | `curl -s -X POST $API/wallets/$CASH/adjust-balance -H "$AUTH" -H "$JSON" -d '{"newBalance":1000000}' \| jq` | `201`, `wallet.balance=1000000`, `transaction.type=ADJUSTMENT_IN`, `amount=30000`. Điều chỉnh không tính là thu/chi |
| W10 | Xoá ví có giao dịch | `curl -s -X DELETE $API/wallets/$BANK -H "$AUTH" \| jq .code` | `422`, `WALLET_HAS_TRANSACTIONS` |
| W11 | Lưu trữ ví rồi ghi vào ví đó | `PATCH $API/wallets/$BANK` với `{"archived":true}`, rồi `POST $API/transactions` vào `$BANK` | `GET $API/wallets` chỉ còn 1 ví (`?includeArchived=true` thì 2). Ghi giao dịch: `422`, `WALLET_ARCHIVED` |
| W12 | Số lẻ không hợp lệ | `POST $API/transactions` với `"amount":10.5` vào ví VND | `422`, `INVALID_AMOUNT` |
| W13 | Người khác xem ví | `GET $API/wallets/$CASH` bằng token của user khác | `404` |

## 8. Chuyển tiền & đa tiền tệ

Quy tắc:

- Chuyển tiền tạo **2 giao dịch** (`TRANSFER_OUT` ở ví nguồn, `TRANSFER_IN` ở ví đích) có chung `transferId`.
  Chuyển tiền không bao giờ tính là thu hay chi. Xoá một vế là xoá cả lần chuyển.
- Hai ví cùng tiền tệ phải nhận đúng số tiền gửi. Khác tiền tệ thì gửi kèm `receivedAmount`, hoặc bỏ trống để dùng
  **tỷ giá**: lấy tỷ giá của chính user trước, rồi tới tỷ giá chung; lấy tỷ giá mới nhất có hiệu lực tới ngày giao
  dịch; không có chiều thuận thì dùng nghịch đảo của chiều ngược lại.
- Tỷ giá `base=USD, quote=VND, rate=25000` nghĩa là 1 USD = 25.000 VND.

```bash
USD=$(curl -s -X POST $API/wallets -H "$AUTH" -H "$JSON" -d '{"name":"Đô la","type":"CASH","currency":"USD"}' | jq -r .id)
TRANSFER="{\"fromWalletId\":\"$CASH\",\"toWalletId\":\"$USD\",\"amount\":500000,\"occurredOn\":\"2025-04-10\"}"
```

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| T1 | Chuyển khi chưa có tỷ giá | `curl -s -X POST $API/transfers -H "$AUTH" -H "$JSON" -d "$TRANSFER" \| jq .code` | `422`, `EXCHANGE_RATE_MISSING` |
| T2 | Nhập tỷ giá | `curl -s -X POST $API/exchange-rates -H "$AUTH" -H "$JSON" -d '{"base":"USD","quote":"VND","rate":25000,"effectiveDate":"2025-04-01"}' \| jq` | `200`. Gửi lại cùng cặp + ngày thì cập nhật, không tạo bản ghi mới |
| T3 | Quy đổi thử | `curl -s "$API/exchange-rates/convert?amount=10&from=USD&to=VND&date=2025-04-10" -H "$AUTH" \| jq .converted` | `250000` |
| T4 | Chuyển tiền | `curl -s -X POST $API/transfers -H "$AUTH" -H "$JSON" -H 'Idempotency-Key: fx-1' -d "$TRANSFER" \| jq` | `201`. `in.amount=20.00`, `in.currency=USD` (500.000 × 1/25.000). Gửi lại cùng key trả về cùng `transferId` |
| T5 | Tổng các ví quy về VND | `curl -s "$API/wallets/total?currency=VND&date=2025-04-10" -H "$AUTH" \| jq '{total,missingRates}'` | `total=1000000` (500.000 + 20 × 25.000), `missingRates=[]`. Ví lưu trữ và ví `excludedFromTotal` không được cộng |
| T6 | Thu chi ngày chuyển | `curl -s "$API/transactions/summary?from=2025-04-10&to=2025-04-10" -H "$AUTH" \| jq length` | `0` |
| T7 | Huỷ chuyển tiền | `curl -si -X DELETE $API/transfers/<transferId> -H "$AUTH"` | `204`. Ví USD về `0.00`, ví tiền mặt được hoàn lại |

## 9. Giao dịch định kỳ & ảnh hoá đơn

Quy tắc:

- Lần thứ n luôn tính từ ngày bắt đầu: quy tắc hằng tháng bắt đầu ngày 31 sẽ rơi vào ngày cuối của tháng ngắn hơn
  rồi quay lại ngày 31.
- Khi tạo quy tắc, các lần đã tới hạn (tới hôm nay) được ghi ngay; các lần sau do job hằng ngày ghi (00:30,
  `app.ledger.recurring-cron`). Mỗi lần có Idempotency-Key riêng nên job chạy lại không ghi trùng.
- Tạm dừng (`active=false`) rồi chạy lại thì **không** ghi bù các lần đã lỡ. Ví bị lưu trữ thì quy tắc tự tạm dừng.
- Ảnh đính kèm: JPEG, PNG, WebP, HEIC hoặc PDF, tối đa 5 MB, tối đa 5 file cho mỗi giao dịch.

```bash
RENT=$(curl -s "$API/categories?type=EXPENSE" -H "$AUTH" | jq -r '.[] | select(.code=="BILLS_RENT") | .id')
TCB=$(curl -s -X POST $API/wallets -H "$AUTH" -H "$JSON" -d '{"name":"Techcombank","type":"BANK_ACCOUNT","initialBalance":50000000}' | jq -r .id)
curl -s -X POST $API/recurring-transactions -H "$AUTH" -H "$JSON" \
  -d "{\"walletId\":\"$TCB\",\"categoryId\":\"$RENT\",\"amount\":5000000,\"frequency\":\"MONTHLY\",\"startDate\":\"2025-01-31\",\"endDate\":\"2025-03-31\"}" | jq
```

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| R1 | Tạo quy tắc hằng tháng | (lệnh trên) | `201`, `generatedCount=3`, `active=false`, `nextOccurrence=null` (đã qua ngày kết thúc) |
| R2 | Giao dịch đã ghi | `curl -s "$API/transactions?walletId=$TCB" -H "$AUTH" \| jq '[.items[].occurredOn]'` | `2025-03-31`, `2025-02-28`, `2025-01-31`. Ví còn `35000000` |
| R3 | Đính kèm ảnh | `curl -s -X POST $API/transactions/<id>/attachments -H "$AUTH" -F 'file=@hoa-don.png;type=image/png' \| jq` | `201`, có `id`, `fileName`, `size` |
| R4 | Xem / tải ảnh | `GET $API/transactions/<id>` có `attachments` 1 phần tử; `curl -s $API/attachments/<attachmentId> -H "$AUTH" -o out.png` | Ra đúng file đã gửi |
| R5 | File không cho phép | gửi file `.exe` (`type=application/octet-stream`) | `422`, `ATTACHMENT_TYPE_NOT_ALLOWED` |
| R6 | Xoá ảnh | `curl -si -X DELETE $API/attachments/<attachmentId> -H "$AUTH"` | `204` |

---

## 10. Ngân sách & hoá đơn

Dùng **một user mới** có ví `$CASH` (10.000.000 VND) và lấy id danh mục như mục 7 (`$FOOD`, `$COFFEE`, `RENT` = mã
`BILLS_RENT`).

Quy tắc ngân sách:

- Ngân sách là hạn mức chi cho **một hoặc nhiều danh mục chi** (bỏ trống = mọi khoản chi), có thể giới hạn **một
  ví**, theo kỳ `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY` (lặp lại, `repeat=true` mặc định) hoặc `CUSTOM`
  (`startDate`–`endDate`, không lặp). Không gửi `startDate` thì kỳ bắt đầu tự nhiên: thứ Hai, ngày 1, đầu quý, 1/1.
- **Đã chi** = tổng khoản chi trong kỳ, gồm cả danh mục con, như báo cáo (không tính chuyển tiền, điều chỉnh, giao
  dịch "không tính vào báo cáo"). Khoản chi tiền tệ khác được quy đổi theo tỷ giá; không có tỷ giá thì bỏ qua và báo
  trong `unconvertedCurrencies`.
- **Gợi ý mỗi ngày** = còn lại / số ngày còn lại của kỳ (tính cả hôm nay), làm tròn xuống.
- Trạng thái: `ON_TRACK`, `WARNING` (≥ 80%, `app.budget.warning-percent`), `EXCEEDED` (≥ 100%), `NOT_STARTED`,
  `ENDED`. Khi một khoản chi làm ngân sách chạm 80% hoặc 100% trong kỳ hiện tại, event `BudgetThresholdReached`
  được phát **một lần mỗi mức mỗi kỳ** (mục thông báo sẽ dùng event này).

Quy tắc hoá đơn:

- Hoá đơn định kỳ (`WEEKLY`/`MONTHLY`/`QUARTERLY`/`YEARLY`, `interval`), kỳ thứ n tính từ `firstDueDate` nên không trôi
  ngày. Danh mục phải là danh mục chi, ví không được lưu trữ, số tiền theo tiền tệ của ví.
- **Thanh toán** trả `202` với trạng thái `PENDING`: hoá đơn gửi lệnh sang sổ thu chi, sổ thu chi ghi khoản chi (có
  `source=BILL`) rồi trả lời → `RECORDED` (vài trăm ms). Nếu sổ thu chi từ chối (ví vừa bị lưu trữ…), thanh toán thành
  `FAILED` và kỳ đó **đến hạn lại** (bù trừ).
- Job hằng ngày (00:20, `app.bill.reminder-cron`) phát `BillDueSoon` khi còn đúng `remindDaysBefore` ngày (mặc định
  3) và `BillOverdue` vào ngày sau hạn nếu chưa trả.

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| B1 | Tạo ngân sách tháng | `curl -s -X POST $API/budgets -H "$AUTH" -H "$JSON" -d "{\"name\":\"Ăn uống\",\"amount\":1000000,\"categoryIds\":[\"$FOOD\"],\"period\":\"MONTHLY\"}" \| jq` | `201`, `startDate` = ngày 1 tháng này, `current.state=ON_TRACK`, `current.remaining=1000000` |
| B2 | Ngân sách cho danh mục thu | như B1 với id danh mục `SALARY` | `422 INVALID_CATEGORY` |
| B3 | Chi 850.000 ở Cà phê | `POST $API/transactions` với `"categoryId":"$COFFEE","amount":850000`, rồi `GET $API/budgets/<id>` | `spent=850000`, `percent=85`, `state=WARNING`, `dailySuggestion` = 150.000 / `daysLeft` |
| B4 | Xem ở ngày khác | `GET $API/budgets?date=<ngày tháng trước>` | `state=NOT_STARTED` (ngân sách bắt đầu tháng này) |
| H1 | Tạo hoá đơn | `curl -s -X POST $API/bills -H "$AUTH" -H "$JSON" -d "{\"name\":\"Tiền nhà\",\"amount\":5000000,\"walletId\":\"$CASH\",\"categoryId\":\"$RENT\",\"frequency\":\"MONTHLY\",\"firstDueDate\":\"<hôm nay>\"}" \| jq` | `201`, `nextDueDate` = hôm nay, `daysUntilDue=0`. `GET $API/bills/upcoming?days=7` có hoá đơn này |
| H2 | Thanh toán | `curl -s -X POST $API/bills/<id>/pay -H "$AUTH" \| jq` (body tuỳ chọn: `amount`, `walletId`, `paidOn`) | `202`, `status=PENDING`; vài giây sau `GET $API/bills/<id>` có `payments[0].status=RECORDED` kèm `transactionId`, `nextDueDate` sang tháng sau, ví bị trừ 5tr |
| H3 | Giao dịch sinh ra | `curl -s "$API/transactions?categoryId=$RENT" -H "$AUTH" \| jq '.items[0] \| {type,source,amount}'` | `EXPENSE`, `BILL`, `5000000` |
| H4 | Bỏ qua một kỳ | `curl -s -X POST $API/bills/<id>/skip -H "$AUTH" \| jq .status` | `SKIPPED`, kỳ tiếp theo lùi thêm 1 tháng |
| H5 | Tạm dừng nhắc | `PATCH $API/bills/<id>` với `{"active":false}` | `active=false`, không còn nhắc |

---

## 11. Thông báo: push, cài đặt, nhắc ghi chép

Quy tắc:

- Thông báo luôn được lưu trong app (`GET $API/notifications`, 50 cái mới nhất). Nếu thiết bị đã đăng ký push token
  Expo (`ExponentPushToken[…]`) thì được đẩy qua **Expo Push** (Expo chuyển tiếp sang FCM/APNs). Token Expo báo
  `DeviceNotRegistered` (app đã gỡ) tự bị xoá. Lỗi gửi push chỉ ghi log, không ảnh hưởng thông báo trong app.
- **Cài đặt**: `disabledTypes` = các loại không tạo thông báo; `quietStart`–`quietEnd` (có thể qua nửa đêm) = vẫn lưu
  trong app nhưng **không đẩy push**; nhắc ghi chép hằng ngày bật/tắt và giờ nhắc riêng.
- **Nhắc ghi chép**: job chạy đầu mỗi giờ (`app.notification.daily-reminder-cron`), nhắc người dùng đến giờ nhắc
  theo múi giờ của họ (lấy từ hồ sơ) **nếu hôm nay chưa tự ghi giao dịch nào**; mỗi ngày tối đa một lần.
- Mỗi sự việc chỉ thông báo một lần, kể cả khi job hoặc event chạy lại.

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| N1 | Cảnh báo ngân sách | Sau B3 (chi 85% ngân sách): `curl -s $API/notifications -H "$AUTH" \| jq '.[] \| {type,body}'` | Có đúng 1 `BUDGET_WARNING`: "Ngân sách "Ăn uống" đã dùng 850.000 VND / 1.000.000 VND (85%)." |
| N2 | Xem / đổi cài đặt | `curl -s $API/notifications/preferences -H "$AUTH" \| jq`; `curl -s -X PUT $API/notifications/preferences -H "$AUTH" -H "$JSON" -d '{"disabledTypes":["BILL_DUE_SOON"],"dailyReminderTime":"21:00","quietStart":"22:00","quietEnd":"07:00"}' \| jq` | Mặc định: `dailyReminderEnabled=true`, `20:00:00`. Sau khi đổi: giá trị mới. Chỉ có `quietStart`: `422 INVALID_QUIET_HOURS` |
| N3 | Đăng ký push | `curl -s -X POST $API/notifications/devices -H "$AUTH" -H "$JSON" -d '{"token":"ExponentPushToken[xxxxxxxxxxxx]","platform":"android","deviceName":"Pixel"}'` | `201`; `GET $API/notifications/devices` có thiết bị. Token sai định dạng: `422 INVALID_PUSH_TOKEN` |
| N4 | Đánh dấu tất cả đã đọc | `curl -s -X POST $API/notifications/read-all -H "$AUTH"` | `{"marked":n}`; `unread-count` về `0` |
| N5 | Huỷ push | `curl -si -X DELETE "$API/notifications/devices/ExponentPushToken%5Bxxxxxxxxxxxx%5D" -H "$AUTH"` (mã hoá `[ ]`) | `204` |

---

## 12. Tài khoản: email, mật khẩu, thiết bị, 2FA, Google, xoá tài khoản

Dùng **một user mới**, đăng ký kèm `deviceName`. Lấy mã trong email:

```bash
code() { curl -s "$API/dev/mailbox?to=$EMAIL" | jq -r '.[0].subject' | grep -oE '[0-9]{6}'; }
```

Quy tắc:

- Đăng ký gửi ngay **mã 6 số** để xác thực email. Mã hết hạn sau 15 phút, nhập sai 5 lần thì mã bị huỷ, chỉ gửi
  lại được sau 1 phút. Email chưa xác thực vẫn dùng app bình thường (`/me` có `emailVerified`).
- **Sai mật khẩu 5 lần** thì tài khoản bị khoá **15 phút**, kể cả khi nhập đúng (`429 ACCOUNT_LOCKED`). Đăng nhập
  đúng hoặc đặt lại mật khẩu thì xoá bộ đếm. Mã 2FA sai cũng được đếm.
- Quên mật khẩu luôn trả `204`, để không lộ email nào đã đăng ký. Đặt lại mật khẩu sẽ đăng xuất **mọi thiết bị**;
  đổi mật khẩu đăng xuất **các thiết bị khác**.
- Mỗi lần đăng nhập là một **phiên (thiết bị)**. Refresh token được xoay vòng; dùng lại token cũ bị coi là bị đánh
  cắp và **cả phiên bị huỷ**. Đăng xuất một thiết bị thì access token của nó vẫn dùng được tới khi hết hạn (15 phút).
- **2FA**: ứng dụng xác thực (Google Authenticator, Authy…) theo chuẩn TOTP. Bật 2FA trả 10 mã khôi phục, mỗi mã
  dùng một lần. Khi bật 2FA, đăng nhập trả `twoFactorRequired=true` và `challengeToken` (5 phút) thay vì token.
- **Google**: app gửi ID token lấy từ Google Sign-In. Backend tự kiểm tra chữ ký, `aud` (client id cấu hình trong
  `GOOGLE_CLIENT_IDS`), hạn dùng và email đã xác thực; miễn phí, không gọi API Google mỗi lần đăng nhập. Chưa cấu
  hình client id thì trả `422 GOOGLE_SIGN_IN_DISABLED`. Email trùng tài khoản đã có thì liên kết vào tài khoản đó;
  nếu tài khoản đó **chưa xác thực email** thì mật khẩu cũ bị xoá (chống chiếm trước địa chỉ email của người khác).
- **Xoá tài khoản** cần gõ lại email và mật khẩu. Dữ liệu ở mọi module (ví, giao dịch, ảnh, tiền gửi, vay nợ, tỷ giá,
  thông báo) bị xoá ngay sau đó, bất đồng bộ qua event `UserDeleted`.

| # | Thao tác | Lệnh | Kỳ vọng |
|---|---|---|---|
| U1 | Hồ sơ | `curl -s $API/me -H "$AUTH" \| jq` | `emailVerified=false`, `hasPassword=true`, `defaultCurrency=VND`, `locale=vi`, `monthStartDay=1` |
| U2 | Xác thực email | `curl -si -X POST $API/me/email/verify -H "$AUTH" -H "$JSON" -d "{\"code\":\"$(code)\"}"` | `204`; `/me` có `emailVerified=true`. Mã sai: `422 INVALID_CODE` |
| U3 | Gửi lại mã ngay | `curl -s -X POST $API/me/email/verification-code -H "$AUTH" \| jq .code` | `RESEND_TOO_SOON` (`429`) trong 1 phút đầu |
| U4 | Quên mật khẩu | `curl -si -X POST $API/auth/forgot-password -H "$JSON" -d "{\"email\":\"$EMAIL\"}"` | `204` (email không tồn tại cũng `204`) |
| U5 | Đặt lại mật khẩu | `curl -si -X POST $API/auth/reset-password -H "$JSON" -d "{\"email\":\"$EMAIL\",\"code\":\"$(code)\",\"newPassword\":\"new-secret1\"}"` | `204`. Refresh token cũ trả `401`; đăng nhập bằng mật khẩu mới được |
| U6 | Danh sách thiết bị | đăng nhập thêm với `"deviceName":"Laptop"`, rồi `curl -s $API/me/sessions -H "$AUTH" \| jq` | Mỗi thiết bị một dòng, `current=true` ở thiết bị đang gọi |
| U7 | Đăng xuất thiết bị | `curl -si -X DELETE $API/me/sessions/<id> -H "$AUTH"`; `DELETE $API/me/sessions` đăng xuất mọi thiết bị khác | `204`. Refresh token của thiết bị đó trả `401` |
| U8 | Sửa hồ sơ | `curl -s -X PATCH $API/me -H "$AUTH" -H "$JSON" -d '{"defaultCurrency":"USD","locale":"en","timeZone":"Asia/Bangkok","monthStartDay":25}' \| jq` | `200`. `monthStartDay` từ 1 đến 28, `locale` là `vi`/`en`, múi giờ IANA |
| U9 | Đổi mật khẩu | `curl -si -X POST $API/me/password -H "$AUTH" -H "$JSON" -d '{"currentPassword":"new-secret1","newPassword":"another-pass1"}'` | `204`. Sai mật khẩu hiện tại: `422 WRONG_PASSWORD` |
| U10 | Khoá tài khoản | đăng nhập sai 5 lần, rồi đăng nhập đúng | 5 lần `401`, lần 6 `429 ACCOUNT_LOCKED` |
| U11 | Bật 2FA | `curl -s -X POST $API/me/2fa/setup -H "$AUTH" \| jq`: quét `otpauthUri` (dạng QR) hoặc nhập `secret` vào ứng dụng xác thực; rồi `POST $API/me/2fa/enable` với `{"code":"<6 số trong app>"}` | `200`, `recoveryCodes` gồm 10 mã. `/me` có `twoFactorEnabled=true` |
| U12 | Đăng nhập có 2FA | `POST $API/auth/login` | `twoFactorRequired=true`, `accessToken=null`, có `challengeToken`. Gửi `POST $API/auth/2fa/verify` với `{"challengeToken":"...","code":"<6 số hoặc mã khôi phục>"}` → token |
| U13 | Tắt 2FA | `POST $API/me/2fa/disable` với `{"code":"..."}` | `204` |
| U14 | Đăng nhập Google | `POST $API/auth/google` với `{"idToken":"<ID token từ Google Sign-In>"}` | Tạo tài khoản lần đầu (`hasPassword=false`, `googleLinked=true`), các lần sau đăng nhập vào tài khoản đó |
| U15 | Xoá tài khoản | `curl -si -X DELETE $API/me -H "$AUTH" -H "$JSON" -d "{\"password\":\"...\",\"confirmEmail\":\"$EMAIL\"}"` | `204`. `GET /me` → `404`, đăng nhập → `401`; vài giây sau ví, giao dịch… đều trống. Gõ sai email: `422 CONFIRMATION_MISMATCH` |

---

## 13. Job đáo hạn, tái tục và thông báo

Phần này cần app đang chạy với cron mỗi 10 giây (mục 1). Tạo các sổ kỳ hạn 2 tháng đã đáo hạn cách đây 3 ngày:

```bash
OPENED=$(date -v-3d -v-2m +%F)                              # macOS
# OPENED=$(date -d "$(date -d '-3 days' +%F) -2 months" +%F) # Linux
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
| `BUDGET_WARNING` / `BUDGET_EXCEEDED` | Ngân sách chạm 80% / 100% trong kỳ hiện tại (mục 10) |
| `BILL_DUE_SOON` / `BILL_OVERDUE` / `BILL_PAYMENT_FAILED` | Hoá đơn còn `remindDaysBefore` ngày / quá hạn hôm qua / sổ thu chi từ chối thanh toán |
| `DAILY_REMINDER` | Đúng giờ nhắc (mặc định 20:00 theo múi giờ hồ sơ) mà hôm nay chưa ghi giao dịch nào |

Đánh dấu đã đọc: `POST $API/notifications/{id}/read` → `204`. Xem số chưa đọc: `GET $API/notifications/unread-count`.

> Mỗi sự việc chỉ được thông báo **một lần** (khoá chống trùng theo loại + đối tượng + ngày), kể cả khi job chạy nhiều
> lần trong ngày (như cron 10 giây ở đây) hoặc app khởi động lại.

---

## 14. Bảng đối chiếu quy tắc nghiệp vụ

| Quy tắc | Kiểm tra ở |
|---|---|
| Ngày đáo hạn = ngày gửi + kỳ hạn; sổ không kỳ hạn không có ngày đáo hạn | 4.1 |
| 3 cách tính lãi cho kết quả đúng công thức | 4.1 |
| Lãi tích luỹ dừng ở ngày đáo hạn | D1 |
| Rút trước hạn tính lãi theo lãi suất không kỳ hạn, báo rõ số lãi bị mất | D4 |
| Không tất toán 2 lần; không tất toán trước ngày gửi | D6, D9 |
| Tái tục gốc / gốc + lãi / không tái tục | Mục 13 |
| Lịch trả nợ đúng cho 4 phương thức, tổng gốc luôn đúng bằng số tiền vay | 5.1 |
| Thanh toán trừ lãi trước gốc, kỳ cũ trước | L2, L8 |
| Gửi lại cùng Idempotency-Key không trừ tiền 2 lần | L3 |
| Không cho trả vượt số nợ | L4 |
| Trả trước hạn: thu phí, tính lại lịch, giữ số kỳ | L5, L6 |
| Phải trả hết kỳ trả dở hoặc quá hạn trước khi trả trước hạn | L9 |
| Lãi thả nổi chỉ áp dụng từ kỳ bắt đầu sau ngày hiệu lực | L7 |
| Trả hết thì khoản vay chuyển sang tất toán và khoá thanh toán | L10, L11 |
| Tài sản ròng = gửi + lãi + cho vay − đi vay; dashboard cập nhật ngay | Mục 6 |
| Người dùng chỉ thấy dữ liệu của mình | D12, W13 |
| Refresh token chỉ dùng được 1 lần | A3, A4 |
| Số dư ví = số dư ban đầu + các giao dịch; sửa, chuyển ví, xoá giao dịch đều cập nhật số dư | W2, W7, W8 |
| Lọc theo danh mục cha gồm cả danh mục con; chuyển tiền và điều chỉnh không tính là thu/chi | W4, W9, T6 |
| Không xoá ví có giao dịch; ví lưu trữ không nhận giao dịch mới | W10, W11 |
| Số tiền đúng số lẻ của tiền tệ | W12 |
| Chuyển khác tiền tệ dùng tỷ giá của user, nghịch đảo khi cần; tổng ví quy đổi về một tiền tệ | T1–T5 |
| Gửi lại cùng Idempotency-Key không ghi/chuyển 2 lần | W2, T4 |
| Giao dịch định kỳ đúng lịch, không trôi ngày, không ghi trùng | R1, R2 |
| Mã email hết hạn, giới hạn số lần nhập sai và thời gian chờ gửi lại | U2, U3 |
| Quên mật khẩu không lộ email; đặt lại mật khẩu đăng xuất mọi thiết bị | U4, U5 |
| Sai mật khẩu 5 lần thì khoá 15 phút | U10 |
| Refresh token xoay vòng theo phiên; đăng xuất từng thiết bị | U6, U7 |
| 2FA: cần mã khi đăng nhập, mã khôi phục dùng một lần | U11–U13 |
| Xoá tài khoản xoá dữ liệu ở mọi module | U15 |
| Ngân sách tính cả danh mục con, quy đổi tiền tệ, gợi ý mức chi mỗi ngày | B1–B4 |
| Thanh toán hoá đơn ghi khoản chi qua sổ thu chi (saga), kỳ sau không trôi ngày | H1–H4 |
| Thông báo chống trùng, tôn trọng loại bị tắt và giờ yên lặng | N1, N2, mục 13 |
