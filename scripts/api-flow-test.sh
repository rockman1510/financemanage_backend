#!/usr/bin/env bash
# Kiểm chứng business flow qua REST API. Yêu cầu: bash (kể cả bash 3.2 của macOS), curl, jq.
# Chạy được từ zsh: script tự dùng bash qua dòng shebang.
#
#   BASE_URL=http://localhost:8080 ./scripts/api-flow-test.sh
#
# Các kịch bản dùng ngày cố định (2025-...) nên số tiền kỳ vọng là xác định và đã được tính tay
# (xem docs/API_TEST_GUIDE.md). Phần "job" (đáo hạn/tái tục, nhắc nợ) cần chạy app với cron nhanh,
# nếu không sẽ được bỏ qua: đặt RUN_JOB_TESTS=1 khi app được chạy với
#   --app.deposit.maturity-cron="*/10 * * * * *" --app.loan.reminder-cron="*/10 * * * * *"
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
RUN_JOB_TESTS="${RUN_JOB_TESTS:-0}"
export TZ="${APP_TZ:-Asia/Ho_Chi_Minh}"   # phải trùng app.time-zone của backend

PASS=0; FAIL=0; STATUS=""; BODY=""; TOKEN=""

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
section() { printf '\n\033[1;34m== %s ==\033[0m\n' "$*"; }

# req METHOD PATH [JSON_BODY] [EXTRA_HEADER]  -> đặt $STATUS và $BODY
req() {
  local method=$1 path=$2 body=${3:-} extra=${4:-} out
  local args=(-s -w '\n%{http_code}' -X "$method" "$API$path" -H 'Accept: application/json')
  [[ -n "$TOKEN" ]] && args+=(-H "Authorization: Bearer $TOKEN")
  [[ -n "$body" ]] && args+=(-H 'Content-Type: application/json' -d "$body")
  [[ -n "$extra" ]] && args+=(-H "$extra")
  out=$(curl "${args[@]}")
  STATUS=${out##*$'\n'}
  BODY=${out%$'\n'*}
}

j() { jq -r "$1" <<<"$BODY"; }

check() { # check "mô tả" actual expected
  if [[ "$2" == "$3" ]]; then PASS=$((PASS+1)); green "  ✔ $1"
  else FAIL=$((FAIL+1)); red "  ✘ $1 — nhận: '$2', kỳ vọng: '$3'"; fi
}

check_status() { check "$1 → HTTP $2" "$STATUS" "$2"; }

# --- ngày tháng (GNU date hoặc BSD/macOS date) ---
today() { date +%F; }
add_days() { date -d "$(today) $1 days" +%F 2>/dev/null || date -j -v"$( [[ $1 == -* ]] && echo "$1" || echo "+$1")"d +%F; }
minus_month() { date -d "$1 -1 month" +%F 2>/dev/null || date -j -f %F "$1" -v-1m +%F; }

register() { # register EMAIL -> đặt TOKEN, REFRESH
  TOKEN=""
  req POST /auth/register "{\"email\":\"$1\",\"password\":\"secret123\",\"fullName\":\"Nguyễn Văn Test\"}"
  TOKEN=$(j .accessToken); REFRESH=$(j .refreshToken)
}

RUN_ID=$(date +%s)

# =====================================================================================
section "0. Hệ thống"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health")
check "Health check" "$STATUS" "200"

# =====================================================================================
section "1. Xác thực"
EMAIL="user$RUN_ID@example.com"
register "$EMAIL"
check_status "Đăng ký" 201
check "Có access token" "$( [[ -n "$TOKEN" && "$TOKEN" != null ]] && echo yes)" "yes"
check "Thời hạn access token (giây)" "$(j .expiresInSeconds)" "900"

SAVED=$TOKEN; TOKEN=""
req POST /auth/register "{\"email\":\"$(tr '[:lower:]' '[:upper:]' <<<"$EMAIL")\",\"password\":\"secret123\",\"fullName\":\"X\"}"
check_status "Đăng ký trùng email (không phân biệt hoa thường)" 422
check "Mã lỗi" "$(j .code)" "EMAIL_TAKEN"

req POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"sai-mat-khau\"}"
check_status "Đăng nhập sai mật khẩu" 401

req POST /auth/login "{\"email\":\"$EMAIL\",\"password\":\"secret123\"}"
check_status "Đăng nhập đúng" 200
R1=$(j .refreshToken)

req POST /auth/refresh "{\"refreshToken\":\"$R1\"}"
check_status "Refresh token" 200
req POST /auth/refresh "{\"refreshToken\":\"$R1\"}"
check_status "Dùng lại refresh token cũ (đã xoay vòng) bị từ chối" 401

req GET /deposits
check_status "Gọi API khi chưa đăng nhập" 401

TOKEN=$SAVED
req GET /me
check "GET /me trả đúng email" "$(j .email)" "$EMAIL"

# =====================================================================================
section "2. Tiền gửi – xem trước & công thức lãi"
DEP='{"institutionName":"Vietcombank","accountNumber":"VCB-001","type":"TERM","principal":100000000,"currency":"VND",
      "annualRate":6,"earlyWithdrawalRate":0.5,"termMonths":6,"openDate":"2025-01-01","rolloverPolicy":"NONE",
      "calculationMethod":"SIMPLE_ACTUAL_365"}'
req POST /deposits/preview "$DEP"
check_status "Preview sổ có kỳ hạn" 200
check "Ngày đáo hạn = 2025-01-01 + 6 tháng" "$(j .maturityDate)" "2025-07-01"
check "Lãi dự kiến = 100tr × 6% × 181/365" "$(j .expectedInterest)" "2975342"
check "Tổng nhận khi đáo hạn" "$(j .totalAtMaturity)" "102975342"

req POST /deposits/preview "${DEP/SIMPLE_ACTUAL_365/SIMPLE_30_360}"
check "Lãi 30/360 = 100tr × 6% × 180/360" "$(j .expectedInterest)" "3000000"

req POST /deposits/preview '{"institutionName":"ACB","type":"TERM","principal":100000000,"annualRate":12,"termMonths":12,
  "openDate":"2025-01-01","calculationMethod":"COMPOUND_MONTHLY"}'
check "Lãi kép tháng = 100tr × (1.01^12 − 1)" "$(j .expectedInterest)" "12682503"

req POST /deposits/preview '{"institutionName":"TCB","type":"DEMAND","principal":10000000,"annualRate":0.5,"openDate":"2025-01-01"}'
check "Không kỳ hạn: không có ngày đáo hạn" "$(j .maturityDate)" "null"
check "Không kỳ hạn: lãi 1 năm = 10tr × 0.5%" "$(j .expectedInterest)" "50000"

section "3. Tiền gửi – vòng đời sổ"
req POST /deposits "$DEP"
check_status "Mở sổ" 201
DEP_ID=$(j .id)
check "Trạng thái ban đầu" "$(j .status)" "ACTIVE"
check "Giao dịch đầu tiên là OPEN" "$(j '.transactions[0].type')" "OPEN"
check "Lãi tích luỹ bị chặn tại ngày đáo hạn (hôm nay đã quá 2025-07-01)" "$(j .accruedInterest)" "2975342"

req GET "/deposits?status=ACTIVE"
check "Lọc danh sách theo ACTIVE" "$(j 'length')" "1"
req GET "/deposits?status=SETTLED"
check "Lọc danh sách theo SETTLED" "$(j 'length')" "0"

req PATCH "/deposits/$DEP_ID" '{"institutionName":"BIDV","note":"Quỹ khẩn cấp"}'
check "Sửa thông tin sổ" "$(j .institutionName)|$(j .note)" "BIDV|Quỹ khẩn cấp"

req GET "/deposits/$DEP_ID/settlement-quote?date=2025-04-01"
check "Báo giá tất toán 2025-04-01 là rút trước hạn" "$(j .early)" "true"
check "Lãi rút trước hạn = 100tr × 0.5% × 90/365" "$(j .interest)" "123288"
check "Tiền lãi bị mất = 1.479.452 − 123.288" "$(j .interestLostByEarlyWithdrawal)" "1356164"
check "Tổng nhận" "$(j .total)" "100123288"

req GET "/deposits/$DEP_ID/settlement-quote?date=2025-07-01"
check "Báo giá đúng ngày đáo hạn: không phải trước hạn" "$(j .early)|$(j .interest)" "false|2975342"

req GET "/deposits/$DEP_ID/settlement-quote?date=2024-12-31"
check_status "Báo giá trước ngày mở sổ" 422

req POST "/deposits/$DEP_ID/settle" '{"date":"2025-04-01"}'
check_status "Tất toán trước hạn" 200
req GET "/deposits/$DEP_ID"
check "Sau tất toán: trạng thái & số tiền" "$(j .status)|$(j .settledAmount)|$(j .settledOn)" "SETTLED|100123288|2025-04-01"
check "Có giao dịch SETTLE" "$(j '.transactions[-1].type')" "SETTLE"
check "Lãi tích luỹ của sổ đã tất toán = 0" "$(j .accruedInterest)" "0"

req POST "/deposits/$DEP_ID/settle"
check_status "Tất toán lần 2" 422
check "Mã lỗi" "$(j .code)" "DEPOSIT_ALREADY_SETTLED"

section "4. Tiền gửi – validation & phân quyền"
req POST /deposits '{"type":"TERM","principal":-5,"annualRate":6,"openDate":"2025-01-01"}'
check_status "Dữ liệu sai" 400
check "Báo lỗi từng trường" "$(j '.fieldErrors | has("principal") and has("institutionName")')" "true"
req POST /deposits '{"institutionName":"X","type":"TERM","principal":1000,"annualRate":6,"openDate":"2025-01-01"}'
check_status "Sổ có kỳ hạn nhưng thiếu kỳ hạn" 422
check "Mã lỗi" "$(j .code)" "INVALID_TERM"

OWNER_TOKEN=$TOKEN
register "stranger$RUN_ID@example.com"
req GET "/deposits/$DEP_ID"
check_status "Người khác xem sổ của tôi" 404
TOKEN=$OWNER_TOKEN

req DELETE "/deposits/$DEP_ID"
check_status "Xoá sổ" 204
req GET "/deposits/$DEP_ID"
check_status "Xem sổ đã xoá" 404

# =====================================================================================
section "5. Vay nợ – lịch trả nợ theo từng phương thức"
LOAN='{"counterpartyName":"VPBank","direction":"BORROW","type":"UNSECURED","principal":120000000,"currency":"VND",
       "annualRate":12,"termMonths":12,"startDate":"2025-01-15","repaymentMethod":"EQUAL_PRINCIPAL","earlyRepaymentFeeRate":1}'
req POST /loans/preview-schedule "$LOAN"
check_status "Preview gốc đều" 200
check "Số kỳ" "$(j '.installments | length')" "12"
check "Kỳ 1: hạn, gốc, lãi (120tr × 1%)" "$(j '.installments[0] | "\(.dueDate)|\(.principalDue)|\(.interestDue)"')" "2025-02-15|10000000|1200000"
check "Kỳ 12: lãi = 10tr × 1%" "$(j '.installments[11].interestDue')" "100000"
check "Tổng lãi = 1% × (120+110+…+10)tr" "$(j .totalInterest)" "7800000"

req POST /loans/preview-schedule '{"counterpartyName":"A","direction":"BORROW","principal":100000000,"annualRate":12,
  "termMonths":12,"startDate":"2025-01-15","repaymentMethod":"ANNUITY"}'
check "Annuity: kỳ 1 trả 8.884.879 (= 100tr × 1% / (1 − 1.01^−12))" "$(j '.installments[0].totalDue')" "8884879"
check "Annuity: các kỳ chênh nhau ≤ 5đ" "$(j '[.installments[].totalDue] | (max - min) <= 5')" "true"
check "Annuity: tổng gốc = 100tr" "$(j '[.installments[].principalDue] | add')" "100000000"

req POST /loans/preview-schedule '{"counterpartyName":"A","direction":"BORROW","principal":12000000,"annualRate":12,
  "termMonths":3,"startDate":"2025-01-15","repaymentMethod":"BULLET"}'
check "Gốc cuối kỳ: gốc từng kỳ" "$(j '[.installments[].principalDue] | join(",")')" "0,0,12000000"
check "Gốc cuối kỳ: lãi mỗi kỳ 120.000" "$(j '[.installments[].interestDue] | unique | join(",")')" "120000"

req POST /loans/preview-schedule '{"counterpartyName":"Bình","direction":"LEND","principal":10000000,"annualRate":10,
  "termMonths":3,"startDate":"2025-01-15","repaymentMethod":"INTEREST_FREE"}'
check "Không lãi: bỏ qua lãi suất, kỳ cuối nhận phần lẻ" "$(j '[.installments[].principalDue] | join(",")')|$(j .totalInterest)" "3333333,3333333,3333334|0"

section "6. Vay nợ – thanh toán"
req POST /loans "$LOAN"
check_status "Tạo khoản vay" 201
LOAN_ID=$(j .summary.id)
check "Dư nợ ban đầu" "$(j .summary.outstandingPrincipal)" "120000000"
check "Các kỳ đã qua hạn được đánh dấu OVERDUE (hôm nay > 2026-01-15)" "$(j .summary.overdueInstallments)" "12"

req POST "/loans/$LOAN_ID/repayments" '{"amount":11200000,"paidOn":"2025-02-15"}' "Idempotency-Key: pay-$RUN_ID"
check_status "Trả kỳ 1" 201
REP_ID=$(j .id)
check "Phân bổ: lãi trước rồi gốc" "$(j .interestPortion)|$(j .principalPortion)" "1200000|10000000"

req POST "/loans/$LOAN_ID/repayments" '{"amount":11200000,"paidOn":"2025-02-15"}' "Idempotency-Key: pay-$RUN_ID"
check "Gửi lại cùng Idempotency-Key → trả về giao dịch cũ" "$(j .id)" "$REP_ID"
req GET "/loans/$LOAN_ID"
check "Chỉ ghi nhận 1 lần" "$(j '.repayments | length')|$(j .summary.outstandingPrincipal)" "1|110000000"
check "Kỳ 1 = PAID" "$(j '.installments[0].status')" "PAID"

req POST "/loans/$LOAN_ID/repayments" '{"amount":999999999,"paidOn":"2025-02-15"}'
check_status "Trả vượt số còn nợ" 422
check "Mã lỗi" "$(j .code)" "OVERPAYMENT"

section "7. Vay nợ – trả trước hạn & đổi lãi suất"
req POST "/loans/$LOAN_ID/prepayments" '{"principal":55000000,"paidOn":"2025-02-20"}' "Idempotency-Key: pre-$RUN_ID"
check_status "Trả trước 55tr" 201
check "Phí 1% và tổng tiền" "$(j .feePortion)|$(j .amount)" "550000|55550000"
req GET "/loans/$LOAN_ID"
check "Dư nợ còn 55tr" "$(j .summary.outstandingPrincipal)" "55000000"
check "Vẫn giữ 12 kỳ" "$(j '.installments | length')" "12"
check "Kỳ 2 tính lại: gốc 55tr/11, lãi 55tr × 1%" "$(j '.installments[1] | "\(.principalDue)|\(.interestDue)"')" "5000000|550000"

req POST "/loans/$LOAN_ID/rate-changes" '{"effectiveFrom":"2025-07-15","annualRate":24}'
check_status "Đổi lãi suất thả nổi từ 2025-07-15" 200
check "Có 2 giai đoạn lãi suất" "$(j '.ratePeriods | length')" "2"
check "Kỳ 6 (bắt đầu 15/06, dư nợ 35tr) giữ 1%/tháng" "$(j '.installments[5].interestDue')" "350000"
check "Kỳ 7 (bắt đầu 15/07, dư nợ 30tr) áp dụng 2%/tháng" "$(j '.installments[6].interestDue')" "600000"

req POST "/loans/$LOAN_ID/repayments" '{"amount":500000,"paidOn":"2025-03-01"}'
check "Trả một phần 500k chỉ vào lãi kỳ 2" "$(j .interestPortion)|$(j .principalPortion)" "500000|0"
req POST "/loans/$LOAN_ID/prepayments" '{"principal":1000000,"paidOn":"2025-03-02"}'
check_status "Trả trước khi còn kỳ trả dở" 422
check "Mã lỗi" "$(j .code)" "SETTLE_DUE_INSTALLMENTS_FIRST"

section "8. Vay nợ – tất toán"
req GET "/loans/$LOAN_ID"
REMAINING=$(j '[.installments[].remaining] | add')
req POST "/loans/$LOAN_ID/repayments" "{\"amount\":$REMAINING,\"paidOn\":\"2025-03-15\"}"
check_status "Trả toàn bộ số còn lại ($REMAINING)" 201
req GET "/loans/$LOAN_ID"
check "Khoản vay chuyển PAID_OFF, dư nợ 0" "$(j .summary.status)|$(j .summary.outstandingPrincipal)" "PAID_OFF|0"
req POST "/loans/$LOAN_ID/repayments" '{"amount":1000,"paidOn":"2025-03-16"}'
check "Không nhận thanh toán thêm" "$STATUS|$(j .code)" "422|LOAN_NOT_ACTIVE"
req GET "/loans?status=PAID_OFF"
check "Lọc khoản vay đã tất toán" "$(j 'length')" "1"

# =====================================================================================
section "9. Dashboard (người dùng mới để số liệu sạch)"
register "dash$RUN_ID@example.com"
req GET /dashboard
check "Chưa có dữ liệu" "$(j '.totals | length')|$(j .activeDeposits)" "0|0"

OPEN=$(add_days -15)
req POST /deposits "{\"institutionName\":\"ACB\",\"type\":\"TERM\",\"principal\":50000000,\"annualRate\":5,\"termMonths\":1,\"openDate\":\"$OPEN\"}"
DASH_DEP_TOTAL=$(( 50000000 + $(j .expectedInterestAtMaturity) ))
req GET /dashboard
ACCRUED=$(j '.totals[0].accruedInterest')
check "Tổng tiền gửi" "$(j '.totals[0].deposits')" "50000000"
check "Lãi tích luỹ 15 ngày = 50tr × 5% × 15/365" "$ACCRUED" "102740"
check "Sắp tới: sổ đáo hạn (tiền vào = gốc + lãi)" "$(j '.upcoming[0] | "\(.direction)|\(.amount)"')" "IN|$DASH_DEP_TOTAL"

req POST /loans "{\"counterpartyName\":\"Bình\",\"direction\":\"BORROW\",\"principal\":20000000,\"termMonths\":1,\"startDate\":\"$(add_days -10)\",\"repaymentMethod\":\"INTEREST_FREE\"}"
req POST /loans "{\"counterpartyName\":\"An\",\"direction\":\"LEND\",\"principal\":5000000,\"termMonths\":1,\"startDate\":\"$(add_days -10)\",\"repaymentMethod\":\"INTEREST_FREE\"}"
req GET /dashboard
check "Cache dashboard được làm mới sau khi thêm khoản vay" "$(j .activeLoans)" "2"
check "Đi vay / cho vay" "$(j '.totals[0] | "\(.borrowedOutstanding)|\(.lentOutstanding)"')" "20000000|5000000"
check "Tài sản ròng = 50tr + lãi + 5tr − 20tr" "$(j '.totals[0].netWorth')" "$(( 50000000 + ACCRUED + 5000000 - 20000000 ))"
check "Sắp tới có 1 khoản chi và 2 khoản thu" "$(j '[.upcoming[] | .direction] | sort | join(",")')" "IN,IN,OUT"

# =====================================================================================
section "10. Job đáo hạn/tái tục & thông báo"
if [[ "$RUN_JOB_TESTS" != "1" ]]; then
  echo "  (bỏ qua – chạy app với cron nhanh và đặt RUN_JOB_TESTS=1, xem docs/API_TEST_GUIDE.md)"
else
  register "job$RUN_ID@example.com"
  OPENED=$(minus_month "$(minus_month "$(add_days -3)")")   # đáo hạn (kỳ hạn 2 tháng) cách đây 3 ngày
  mk() { req POST /deposits "{\"institutionName\":\"$1\",\"type\":\"TERM\",\"principal\":100000000,\"annualRate\":6,\"termMonths\":2,\"openDate\":\"$OPENED\",\"rolloverPolicy\":\"$2\"}"; }
  mk "NoRoll" NONE;                     ID_NONE=$(j .id)
  mk "RollPI" PRINCIPAL_AND_INTEREST;   ID_PI=$(j .id); INT_PI=$(j .expectedInterestAtMaturity); MAT_PI=$(j .maturityDate)
  mk "RollP"  PRINCIPAL_ONLY;           ID_P=$(j .id)
  TARGET=$(add_days 7)
  req POST /deposits "{\"institutionName\":\"SoonBank\",\"type\":\"TERM\",\"principal\":1000000,\"annualRate\":5,\"termMonths\":1,\"openDate\":\"$(minus_month "$TARGET")\"}"
  SOON_MATURITY=$(j .maturityDate)
  req POST /loans "{\"counterpartyName\":\"DueSoon\",\"direction\":\"BORROW\",\"principal\":3000000,\"termMonths\":1,\"startDate\":\"$(minus_month "$(add_days 3)")\",\"repaymentMethod\":\"INTEREST_FREE\"}"
  req POST /loans "{\"counterpartyName\":\"Late\",\"direction\":\"LEND\",\"principal\":2000000,\"termMonths\":1,\"startDate\":\"$(minus_month "$(add_days -1)")\",\"repaymentMethod\":\"INTEREST_FREE\"}"

  echo "  … chờ job chạy (tối đa 60 giây)"
  for _ in $(seq 1 30); do
    req GET "/deposits/$ID_NONE"; [[ "$(j .status)" == "MATURED" ]] && break; sleep 2
  done
  check "Không tái tục → MATURED" "$(j .status)" "MATURED"
  req GET "/deposits/$ID_PI"
  check "Tái tục gốc + lãi: gốc mới = 100tr + lãi kỳ trước" "$(j .principal)" "$(( 100000000 + INT_PI ))"
  check "Tái tục gốc + lãi: kỳ mới bắt đầu từ ngày đáo hạn cũ" "$(j .openDate)|$(j .status)" "$MAT_PI|ACTIVE"
  req GET "/deposits/$ID_P"
  check "Tái tục gốc: gốc giữ nguyên, lãi được chi trả" "$(j .principal)|$(j '[.transactions[].type] | join(",")')" "100000000|OPEN,INTEREST_PAID,ROLLOVER"

  sleep 3
  req GET /notifications
  TYPES=$(j '[.[].type] | unique | join(",")')
  echo "  Thông báo nhận được: $TYPES"
  check "Có thông báo sổ đã đáo hạn" "$(grep -c DEPOSIT_MATURED <<<"$TYPES")" "1"
  if [[ "$SOON_MATURITY" == "$TARGET" ]]; then
    check "Có nhắc sổ sắp đáo hạn (7 ngày)" "$(grep -c DEPOSIT_MATURING_SOON <<<"$TYPES")" "1"
  fi
  check "Có nhắc kỳ trả nợ sắp tới (3 ngày)" "$(grep -c INSTALLMENT_DUE_SOON <<<"$TYPES")" "1"
  check "Có cảnh báo quá hạn (hạn hôm qua)" "$(grep -c INSTALLMENT_OVERDUE <<<"$TYPES")" "1"
  N_ID=$(j '.[0].id')
  req POST "/notifications/$N_ID/read"
  check_status "Đánh dấu đã đọc" 204
fi

# =====================================================================================
printf '\n'
if (( FAIL == 0 )); then green "TẤT CẢ $PASS KIỂM TRA ĐỀU ĐẠT"; else red "$FAIL KIỂM TRA LỖI / $((PASS+FAIL))"; fi
exit $(( FAIL > 0 ))
