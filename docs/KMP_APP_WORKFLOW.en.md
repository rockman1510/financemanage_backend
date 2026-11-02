# Workflow: building the Finance Manage app (KMP) with Claude Code CLI

> Vietnamese version: [KMP_APP_WORKFLOW.vi.md](KMP_APP_WORKFLOW.vi.md)

A single workflow for Claude Code CLI on your computer to build the **complete** personal finance app for **Android
+ iOS** with Kotlin Multiplatform, connected to the backend in this repository.

- **Part A** – setup (once).
- **Part B** – the **master prompt**: paste it once; Claude builds every feature in order from the blueprint and stops
  after each one so you can check it.
- **Part C** – the **blueprint** of every feature: screens, components, flows, API, rules, errors, done criteria.

The app's API documentation: [MOBILE_API_GUIDE.en.md](MOBILE_API_GUIDE.en.md) (**API §n** = section n of that file) and
[api/openapi.json](api/openapi.json).

---

## Part A – Setup

1. **Tools**: Claude Code CLI (`npm install -g @anthropic-ai/claude-code`), JDK 21, Android Studio (Android SDK +
   emulator, Kotlin Multiplatform plugin), **macOS + Xcode** to build/run iOS (without a Mac: shared code + Android,
   iOS checked on CI), Docker Desktop, Git.
2. **Folders and documents**:

   ```bash
   mkdir -p ~/projects && cd ~/projects
   git clone https://github.com/rockman1510/financemanage_backend.git
   git clone https://github.com/rockman1510/finance-management-mobile.git
   mkdir -p finance-management-mobile/docs/backend
   cp financemanage_backend/docs/MOBILE_API_GUIDE.en.md financemanage_backend/docs/api/openapi.json \
      financemanage_backend/docs/KMP_APP_WORKFLOW.en.md finance-management-mobile/docs/backend/
   ```

3. **Local backend** (keep it running): `cd ~/projects/financemanage_backend && docker compose up --build` →
   `http://localhost:8080` (Android emulator: `http://10.0.2.2:8080`). To read e-mail codes while testing, run the dev
   profile instead: `cd backend && ./gradlew :app:bootRun --args='--spring.profiles.active=dev'`; codes are at
   `GET /api/v1/dev/mailbox?to=<email>`.
4. **Fewer permission prompts** – create `finance-management-mobile/.claude/settings.json`:

   ```json
   {"permissions": {"allow": ["Bash(./gradlew:*)", "Bash(git:*)", "Bash(xcodebuild:*)", "Bash(xcrun simctl:*)",
     "Bash(adb:*)", "Bash(curl:*)", "Bash(jq:*)"]}}
   ```

5. **Run**: `cd ~/projects/finance-management-mobile && claude`, press **Shift+Tab** to enter plan mode, paste the
   **master prompt** (Part B). When Claude stops after a feature: try the app, report problems if any, then type
   `continue`. In a long session use `/compact`; reopen a session with `claude --continue`; in a new session paste the
   master prompt again – Claude resumes from the first unfinished feature in `docs/PLAN.md`.

---

## Part B – Master prompt

```text
You are the lead mobile engineer of Finance Manage – a personal finance app in Vietnamese for Android and iOS,
written with Kotlin Multiplatform + Compose Multiplatform, connected to an existing REST backend.

DOCUMENTS (read them fully before starting, look things up again whenever needed, never guess the API):
- docs/backend/KMP_APP_WORKFLOW.en.md – Part C is the BLUEPRINT of every feature (C0 shared foundation, F01–F22).
- docs/backend/MOBILE_API_GUIDE.en.md – how to use the API, business rules, error codes, sample JSON ("API §n" =
  section n).
- docs/backend/openapi.json – the exact request/response types.

The app's UI language is Vietnamese: every user-facing string from the blueprint goes into resources in Vietnamese
(with an English translation prepared).

PROCESS:
1. Initialisation (if not done yet):
   a. Write CLAUDE.md from blueprint C0 (technologies, architecture, conventions, the project's real build/test
      commands).
   b. Write docs/PLAN.md: a table of F01–F22 in blueprint order; for each feature its screens, endpoints and the
      "Done when" checklist copied from the blueprint; a status column (todo / in progress / done).
   c. Scaffold the project as described in C0 (modules, DI, theme, 5-tab navigation, per-platform baseUrl, Android +
      iOS CI) so that it builds.
2. Then each feature F01 → F22 in order (skip features marked "done" in docs/PLAN.md):
   a. Read the feature's blueprint and the API sections it references; present a short plan (files, screens, tests).
   b. Implement every screen, component, flow, rule and error case of the blueprint – leave nothing out and add no
      features beyond the blueprint.
   c. Write the tests listed under "Tests" in the blueprint.
   d. Build Android and run all tests; on macOS also build for the iOS simulator. Fix until green.
   e. Review your own changes against CLAUDE.md (BigDecimal money, Idempotency-Key, PENDING, errors,
      loading/empty/error states).
   f. Tick the checklist in docs/PLAN.md and commit with the message "feat(Fxx): <feature name>".
   g. STOP: summarise (what was built, how to check it on the emulator against the real backend, anything uncertain)
      and wait until I answer "continue" or report problems. When I report a problem: find the root cause, fix it,
      add a test, then stop again.
3. If a feature needs more from the backend (e.g. a new endpoint): do not fake data; add it to the "Backend requests"
   section of docs/PLAN.md and tell me.
4. When every feature is done: run the release checklist (blueprint F22) and report the result.

Start with step 1.
```

---

## Part C – Blueprint

### C0. Shared foundation (applies to every feature)

**Technologies**: Kotlin Multiplatform, Compose Multiplatform (Material 3), Navigation Compose, Lifecycle ViewModel,
Ktor Client (OkHttp / Darwin) + kotlinx.serialization, kotlinx-coroutines, kotlinx-datetime, Koin, Room KMP, DataStore,
`com.ionspin.kotlin:bignum`, Coil 3, Vico (or Canvas) for charts, Firebase Messaging, ktlint/detekt.
Root package `com.financemanage.mobile`; versions declared in `gradle/libs.versions.toml`.

**Module architecture**

| Module | Role |
|---|---|
| `composeApp` | Android/iOS entry point, NavHost, theme, Koin start-up |
| `core/model` | `Money`, `CurrencyCode`, `AppError`, shared types |
| `core/network` | HttpClient, auth/refresh, error mapping, Idempotency-Key, PENDING polling |
| `core/data` | Room, DataStore, secure token storage (Keystore/Keychain – expect/actual), network state |
| `core/ui` | Design system: colours, typography, `MoneyText`, `AmountKeypad`, `CategoryIcon`, `ProgressBar`, `EmptyState`, `ErrorState`, skeletons, `ConfirmDialog`, `DateRangePicker`, charts |
| `core/testing` | Fakes, MockEngine, sample JSON |
| `feature/*` | One per feature: `data` (DTO, Api, Repository) / `domain` (models, use cases) / `presentation` (ViewModel, Screen). Features never depend on each other |
| `iosApp` | Xcode project |

**Navigation**

- Signed out: Welcome → Sign in / Sign up / Forgot password → (Onboarding the first time).
- Signed in – bottom bar with 5 items: **Home** · **Transactions** · **➕** (bottom sheet: Expense, Income, Transfer) ·
  **Plan** (top tabs: Budgets, Bills, Savings) · **Assets** (Wallets, Deposits, Loans, Investments, Net worth).
- Home top bar: notification bell (unread badge), avatar → Settings. Reports open from Home.
- Deep link `financemanage://<referenceType>/<id>` used when a notification is tapped.

**Mandatory conventions**

- **Money**: BigDecimal (bignum), never `Double`/`Float`. The serializer reads the raw content of the JSON number.
  Display with the `vi-VN` locale: `1.250.000 ₫`, `12,50 US$`; VND has 0 decimals, USD 2. Negative red, positive green.
  Send amounts with the currency's number of decimals.
- **Dates**: `LocalDate` `yyyy-MM-dd`; "today" in the profile's `timeZone`; shown as `dd/MM/yyyy`, grouped "Hôm nay"
  (Today), "Hôm qua" (Yesterday).
- **JSON**: `ignoreUnknownKeys = true`, `explicitNulls = false`; enums have an `UNKNOWN` fallback.
- **Auth**: access token 15 minutes; refresh token usable **once** (Ktor `Auth` bearer + `refreshTokens`, a single
  refresh at a time); failed refresh → clear tokens, emit a signed-out event, go to Sign in.
- **Errors**: any non-2xx response → `AppError(status, code, message, fieldErrors)`; the UI translates `code` into
  Vietnamese (code table in API §3) and shows `fieldErrors` under each field; `409` → reload and retry; `429` → show the
  waiting time; offline → banner.
- **Idempotency-Key**: a UUID created when Save is tapped and kept for every retry of the same action: `POST
  /transactions`, `/transfers`, `/loans/{id}/repayments`, `/loans/{id}/prepayments`.
- **PENDING** (API §2.1): after `202`/`PENDING` show "Processing…", poll the object every 1.5 s for up to 10 s;
  `RECORDED` → success + refresh wallets; `FAILED` → show `failureReason`; timeout → "Will update shortly", refresh when
  the screen is shown again.
- **Screens**: every screen has loading (skeleton), empty (illustration + action button) and error (retry button)
  states; pull to refresh; refresh when the app returns to the foreground. Immutable State + Event + ViewModel; no API
  calls inside composables.
- **Strings** in Vietnamese in resources (English prepared). Support dark mode, dynamic font size, `contentDescription`.
- **Secrets**: never commit `google-services.json`, `GoogleService-Info.plist` or keystores.

**Commands** (put in CLAUDE.md, adjust to the real project): `./gradlew :composeApp:assembleDebug`,
`./gradlew :composeApp:installDebug`, `./gradlew allTests`, `./gradlew ktlintCheck detekt`; iOS:
`./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` then
`xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build`.

**CI**: GitHub Actions – Android build + tests (ubuntu-latest), iOS build (macos-latest).

---

### F01. App foundation

- **Goal**: a running skeleton every later feature plugs into.
- **Components**: `HttpClient` (baseUrl: Android emulator `10.0.2.2:8080`, iOS simulator `localhost:8080`, production
  `https://<DOMAIN>` per build type; cleartext/ATS exceptions only in debug), `Auth` plugin, `ErrorMapper`,
  `IdempotencyKeys`, `PendingPoller`, `TokenStore` (expect/actual), `NetworkMonitor`, `MoneyFormatter`,
  `MoneySerializer`, the `core/ui` design system, NavHost with 5 tabs (empty screens), light/dark theme.
- **API**: §1, §2, §3.
- **Done when**: the app runs on Android + iOS showing the 5 tabs; `MoneyFormatter` is correct for VND/USD; every HTTP
  error maps to `AppError`; CI is green.
- **Tests**: MoneySerializer (keeps `40.00`, `94.67`), MoneyFormatter, ErrorMapper (400 with fieldErrors, empty 401,
  422, 429), Auth refresh (single use, 401 on refresh → signed out) with MockEngine.

### F02. Sign up, sign in

- **Screens**: Welcome (logo, Sign in, Create account) · Sign up (full name, e-mail, password 8–72 with show/hide) · Sign
  in (e-mail, password, Forgot password?, **Google button on Android only**) · Two-step verification (6-digit code with
  auto-advancing boxes; "Use a recovery code" link) · Forgot password (e-mail → 6-digit code + new password).
- **Flows**: successful sign-up → Onboarding (F03); **2FA is optional**: accounts without 2FA go straight into the app
  with no 2FA step; only when sign-in returns `twoFactorRequired` does the 2FA screen appear, with `challengeToken`
  (expires after 5 minutes → back to Sign in); Google: Credential Manager gets an ID token →
  `/auth/google`; password reset done → back to Sign in with a message; send `deviceName` (device model).
- **API**: §4.1 – `POST /auth/register`, `/auth/login`, `/auth/2fa/verify`, `/auth/google`, `/auth/forgot-password`,
  `/auth/reset-password`, `/auth/logout`.
- **Rules & errors**: `EMAIL_TAKEN` under the e-mail field; `INVALID_CREDENTIALS` "Wrong e-mail or password";
  `ACCOUNT_LOCKED` (429) "Locked for 15 minutes"; `INVALID_CODE`; `GOOGLE_SIGN_IN_DISABLED`; forgot password always says
  "If the e-mail exists, a code was sent".
- **Done when**: sign-up, sign-in, 2FA, forgot/reset password work against the real backend; accounts without 2FA never
  see the 2FA screen; reopening the app keeps the user signed in; an expired refresh token leads to Sign in without loops.
- **Tests**: sign-in ViewModel (normal – no 2FA, with 2FA, errors), token store/clear in the repository.

### F03. Onboarding

- **Screens** (4 steps, progress bar; steps 3 and 4 skippable): (1) Choose the default currency (VND default, search ISO
  codes) · (2) Create the first wallet (suggested name "Tiền mặt" (Cash), type, current balance with `AmountKeypad`) ·
  (3) Create a monthly budget (amount for all expenses) · (4) **Protect your account – optional**: a short explanation
  of two-step verification (a 6-digit code from Google Authenticator at each sign-in), two buttons **"Turn on now"**
  (opens the F04 enable-2FA flow, then comes back) and **"Skip"** (equally prominent, no warning, never asked again).
  Accounts signed in with Google see this step too.
- **API**: `PATCH /me` (defaultCurrency), `POST /wallets`, `POST /budgets`, `/me/2fa/*` when "Turn on now" is chosen
  (§4.2, §5.1, §7).
- **Done when**: shown only once (DataStore flag + check that a wallet exists); skipping step 4 enters the app normally
  with 2FA still off; finishing lands on a Home with data.
- **Tests**: onboarding ViewModel (skip step 3, skip step 4, enable 2FA at step 4).

### F04. Profile, settings, security

- **Settings screen**: profile card (name, e-mail, "Not verified" label) · Profile (full name, default currency,
  language, time zone, month start day 1–28) · E-mail verification (send code, enter code; 60-second countdown before
  resending) · Password (change; Google-only accounts: "Set password" without the current one) · Two-step verification –
  **optional, off by default** (a row showing "Off"/"On"; enable: show a QR code from `otpauthUri` + the `secret` to
  copy → enter a code → show the **10 one-time recovery codes** with copy/save buttons and a required "I saved them"
  confirmation; cancelling half-way leaves 2FA off; disable: enter a TOTP or recovery code, at any time) · A "Make your
  account safer with two-step verification" card at the top of Settings while 2FA is off, with "Turn on" and "Later"
  (hidden for 30 days, stored in DataStore) – it never blocks the user · Signed-in devices
  (list, mark this device, revoke one, "Sign out all other devices") · Notifications (F20) · Exchange rates (F11) · App
  lock (fingerprint/Face ID switch, relock after 1/5/15 minutes) · Hide balances by default · Appearance
  (light/dark/system) · Sign out · **Delete account** (warning, re-enter e-mail + password).
- **API**: §4.2 – `GET/PATCH /me`, `/me/email/*`, `/me/password`, `/me/2fa/*`, `/me/sessions`, `DELETE /me`.
- **Rules & errors**: `WRONG_PASSWORD`, `INVALID_CODE`, `RESEND_TOO_SOON` (429), `CONFIRMATION_MISMATCH`; successful
  account deletion → wipe local data, back to Welcome. App lock uses BiometricPrompt / LocalAuthentication
  (expect/actual) with the device passcode as fallback.
- **Done when**: everything works against the real backend; the whole app is usable without enabling 2FA; after
  enabling 2FA, signing in again goes through the 2FA screen, and no longer after disabling it; revoking another device
  signs it out at its next refresh.
- **Tests**: 2FA ViewModel (setup → enable → recovery codes; cancel half-way; disable), suggestion card (hidden for 30
  days), account deletion (cache wiped).

### F05. Wallets

- **Screens**: Wallet list (Assets tab) – total card converted to the default currency (warning when `missingRates`),
  one card per wallet (icon, name, balance; credit cards add a used/available limit bar and the due date), collapsed
  "Archived" section · Create/Edit wallet (name ≤ 60, type CASH/BANK_ACCOUNT/E_WALLET/CREDIT_CARD/OTHER, currency – on
  create only, initial balance, icon, excluded from total; CREDIT_CARD: limit, statement day, due day 1–31) · Wallet
  detail (balance, the wallet's transactions – reuse the F07 list, Adjust balance, Archive/Unarchive, Delete).
- **API**: §5.1 – `GET/POST /wallets`, `GET/PATCH/DELETE /wallets/{id}`, `POST /wallets/{id}/adjust-balance`,
  `GET /wallets/total`.
- **Rules & errors**: balances may be negative; deleting a wallet with transactions → `WALLET_HAS_TRANSACTIONS` →
  suggest archiving; archived wallets cannot be picked for new transactions; adjusting to the current balance →
  `NOTHING_TO_ADJUST`.
- **Done when**: create/edit/archive/delete/adjust work; the converted total matches the backend; cached data shows at
  once on opening.
- **Tests**: wallet repository, form ViewModel (credit card validation).

### F06. Categories

- **Screens**: Manage categories (Expense/Income tabs; parents expand to sub-categories; default categories get
  icon/colour from `code`) · Create/Edit (name ≤ 60, type, optional parent – 2 levels max, icon picker) · Shared
  category picker (icon grid, "Recent" first, search).
- **API**: §5.2 – `GET /categories?type=`, `POST`, `PATCH`, `DELETE /categories/{id}`.
- **Rules & errors**: `CATEGORY_IN_USE`, `CATEGORY_HAS_CHILDREN`, `CATEGORY_TOO_DEEP`, `CATEGORY_TYPE_MISMATCH`; a
  `code` → icon + colour table in `core/ui` (FOOD, FOOD_RESTAURANT, FOOD_COFFEE, BILLS_*, TRANSPORT_*, SHOPPING_*,
  ENTERTAINMENT, HEALTH, EDUCATION, FAMILY, FRIENDS, GIFTS_DONATIONS, INSURANCE, FEES, OTHER_EXPENSE, SALARY, BONUS,
  INTEREST, SELLING, GIFTS_RECEIVED, OTHER_INCOME); custom categories use the icon the user picked.
- **Done when**: the picker works in every form; "Recent" is stored locally.

### F07. Income and expense transactions

- **Quick add screen** (from ➕, full-screen bottom sheet): Expense/Income toggle · big amount at the top +
  `AmountKeypad` (0–9, `000`, backspace, automatic thousand separators) · category picker (F06) · wallet (defaults to the
  last used) · date (defaults to today, "Yesterday" chip) · "More details": note ≤ 500, person ≤ 120, exclude from
  reports, receipt photos (F10), repeat (F09) · Save (keeps the sheet open when "Add another" is on).
- **Transactions screen** (tab): search bar + filter button · income/expense/net card for the current filter · list
  grouped by day (day header + day totals); each row: category icon, category name, note, wallet, coloured amount,
  source label (Bill / Savings / Investment / Deposit / Transfer / Adjustment) · infinite scroll (size 30) · swipe left
  for Edit/Delete.
- **Filter** (bottom sheet): wallet, categories (multiple), types (multiple), date range (this month, last month,
  custom), amount from–to, keyword; chips for active filters, clear-all button.
- **Transaction detail**: every field, receipt photos, Edit/Delete buttons (hidden for read-only transactions).
- **API**: §5.3 – `POST /transactions` (+Idempotency-Key), `GET /transactions`, `GET /transactions/summary`,
  `GET/PATCH/DELETE /transactions/{id}`.
- **Rules & errors**: amount > 0 with the currency's decimals (`INVALID_AMOUNT`); transactions with a `source`,
  transfers, adjustments and EXTERNAL_* are read-only (`TRANSACTION_NOT_EDITABLE`); `WALLET_ARCHIVED`; after edit/delete
  refresh wallets, budgets and Home.
- **Done when**: an expense is recorded in ≤ 3 taps after opening ➕; combined filters match the backend; retrying after
  a timeout creates no duplicate.
- **Tests**: keypad (formatting, 000, backspace), add ViewModel (Idempotency-Key kept across retries), paging, filter →
  query string.

### F08. Transfers between wallets

- **Screen** (➕ → Transfer): source wallet, destination wallet (different), amount; different currencies: show the
  estimated rate (F11) and an editable "Amount received" field; date, note.
- **API**: §5.4 – `POST /transfers` (+Idempotency-Key), `DELETE /transfers/{transferId}`.
- **Rules & errors**: `SAME_WALLET`, `EXCHANGE_RATE_MISSING` (suggest entering the received amount or adding a rate),
  `TRANSFER_AMOUNT_MISMATCH`; deleting one side deletes both.
- **Done when**: both balances change correctly; cross-currency transfers follow the rate or the received amount.

### F09. Recurring transactions

- **Screens**: "Repeat" switch in Quick add (frequency Daily/Weekly/Monthly/Yearly, every N periods, start date, optional
  end date, a summary sentence such as "Every 2 months on the 15th") · Recurring list (from Settings or the Transactions
  menu): name, amount, next run (`nextOccurrence`), times generated, status · Edit (wallet, category, amount, note, end
  date, pause) · Delete.
- **API**: §5.5 – `/recurring-transactions`.
- **Done when**: a recurrence starting today produces a transaction after the job runs; pause/resume work.

### F10. Receipt photos

- **Components**: a thumbnail row in Quick add and Transaction detail; add button → Take photo / Choose from library /
  Choose PDF; full-screen viewer (zoom); delete.
- **Technique**: camera + library through expect/actual; compress to JPEG < 5 MB; multipart upload with the part named
  `file`; download images with Ktor including the Authorization header, then display them (disk cache).
- **API**: §5.6 – `POST /transactions/{id}/attachments`, `GET/DELETE /attachments/{id}`.
- **Rules & errors**: JPEG/PNG/WebP/HEIC/PDF; `ATTACHMENT_TOO_LARGE`, `ATTACHMENT_TYPE_NOT_ALLOWED`,
  `TOO_MANY_ATTACHMENTS` (max 5); for a new transaction save it first, then upload the photos with progress.
- **Done when**: take photo → save → reopen shows the photo on both platforms.

### F11. Currencies and exchange rates

- **Screens** (Settings → Exchange rates): list of rate pairs (pair, rate, effective date, "Global" label when `global`),
  add (`1 USD = 25,000 VND`, effective date), delete · Converter (amount, from, to, date).
- **Shared**: a "Missing exchange rate" helper – wherever `EXCHANGE_RATE_MISSING` appears, show a message with an "Add
  rate" button that opens the pair pre-filled.
- **API**: §6 – `/exchange-rates`, `/exchange-rates/convert`.

### F12. Home

- **Components** (vertical scroll, each block loads independently, a failing block does not break the screen):
  1. Top bar: greeting, bell (badge from `unread-count`), avatar.
  2. Total balance card (converted) + eye button that hides every amount (remembered).
  3. This month's income/expense + % vs last month (`/reports/summary`) → tap opens Reports.
  4. Budgets: the 3 with the highest % (coloured bars) → "See all".
  5. Bills due in the next 7 days (`/bills/upcoming?days=7`) with a quick Pay button.
  6. The closest saving goal with its progress.
  7. The 5 latest transactions → "See all".
- **API**: §5.1, §7, §8, §9, §13, §14. (If the backend adds `GET /api/v1/home`, switch to that single request.)
- **Done when**: opening the app shows cached data at once, then updates; pull to refresh; hiding balances applies to
  every screen.

### F13. Budgets

- **Screens**: List (Plan → Budgets): each card shows name, period (`01/11–30/11`), spent / limit, a progress bar
  coloured by `state` (ON_TRACK green, WARNING yellow ≥ 80%, EXCEEDED red, NOT_STARTED/ENDED grey), "**98.500 ₫/day**
  left for 30 days", an `unconvertedCurrencies` warning · Detail: daily spending chart for the period,
  previous/next period buttons (`?date=`), the budget's transactions (F07 filtered by categories + wallet + period) ·
  Create/Edit: name, amount, currency, period (Week/Month/Quarter/Year/Custom – Custom needs start/end dates), repeat,
  categories (multiple; empty = every expense), wallet (one or all).
- **API**: §7 – `/budgets`, `/budgets/{id}?date=`.
- **Rules**: a parent category includes its children; spending in other currencies is converted.
- **Done when**: recording an expense updates the progress bar; colours match the thresholds.

### F14. Recurring bills

- **Screens**: List (Plan → Bills) grouped Overdue (red) / Due soon (yellow, ≤ `remindDaysBefore`) / Later; each row:
  name, amount, "N days left"/"N days overdue", **Pay** button · Detail: information, payment history (status, date,
  linked transaction), Pay now / Skip this period / Pause / Edit / Delete · Create/Edit: quick templates (Electricity,
  Water, Internet, Phone, Rent → BILLS_* categories), name, amount, wallet, expense category, frequency
  (Week/Month/Quarter/Year), every N periods, first due date, end date, remind N days before, note · Pay (sheet):
  amount (default), wallet, date.
- **API**: §8 – `/bills`, `/bills/upcoming`, `/bills/{id}/pay` (202), `/bills/{id}/skip`.
- **Rules & errors**: paying follows the PENDING flow (C0); `FAILED` → that period stays due; `BILL_FINISHED`.
- **Done when**: paying a bill creates the expense in the wallet and moves the bill to its next period.

### F15. Saving goals

- **Screens**: List (Plan → Savings): card with a % progress ring, saved / target, deadline, "Save **1.500.000 ₫/month**",
  Auto / Round-up labels · Detail: large progress, Deposit / Withdraw buttons, history (MANUAL/AUTO/ROUND_UP, status),
  **Auto contribution** settings (amount, wallet, Weekly/Monthly, start date; turn off) and **Round-up** settings
  (wallet, multiple of 1,000/5,000/10,000; explanation "Spend 47,000 → save 3,000"; turn off) · Create/Edit: name,
  target amount, currency, deadline (clearable), icon · Deposit/Withdraw (sheet): amount, wallet (same currency), date.
- **API**: §9 – `/saving-goals`, `/deposits`, `/withdrawals` (202), `/auto-contribution`, `/round-up`.
- **Rules & errors**: PENDING; `INSUFFICIENT_SAVINGS`, `GOAL_NOT_EMPTY` (withdraw everything before deleting),
  `ROUND_UP_TAKEN`, `CURRENCY_MISMATCH`; celebration when passing 50% and 100%.
- **Done when**: a deposit lowers the wallet and raises the goal; round-up works when recording an expense in the chosen
  wallet.

### F16. Bank deposits (term savings accounts)

- **Screens**: List (Assets → Deposits; filter Active / Matured / Settled): bank, principal, rate, maturity date
  (countdown), accrued interest · Create: bank, account number, Term/Demand, principal, currency, annual rate %, early
  withdrawal rate, term (months), open date, interest payment (at maturity / monthly / upfront), rollover (none /
  principal / principal + interest), calculation method, note, funding wallet (optional); **preview block updated while
  typing**: maturity date, interest, total · Detail: interest accrued today, expected interest, the deposit's history,
  wallet funding/payout status · Settle (sheet): date, receiving wallet; **quote** principal + interest = total, warning
  "Early withdrawal: you lose X ₫ of interest".
- **API**: §10 – `/deposits`, `/deposits/preview`, `/deposits/{id}/settlement-quote`, `/deposits/{id}/settle`.
- **Rules & errors**: PENDING for `fundingStatus`/`payoutStatus`; `DEPOSIT_ALREADY_SETTLED`, `INVALID_SETTLEMENT_DATE`.
- **Done when**: preview numbers match the backend; early settlement shows the lost interest correctly.

### F17. Loans (borrowed and lent)

- **Screens**: List (Assets → Loans; tabs "I borrowed" / "I lent"; filter Active / Paid off): counterparty, outstanding,
  next installment + amount, "N overdue" label · Create: counterparty, borrow/lend, type, principal, currency, rate,
  months, start date, repayment method (equal principal / annuity / bullet / interest-free), prepayment fee %, note;
  **schedule preview** (installment table + principal/interest chart, total interest, total payment) · Detail:
  outstanding, current rate, installments (PENDING/PARTIAL/PAID/OVERDUE), repayment history (principal/interest/fee),
  rate periods · Repay (amount, date) · Prepay (principal, date; shows the estimated fee) · Change rate (effective date,
  new rate).
- **API**: §11 – `/loans`, `/loans/preview-schedule`, `/repayments`, `/prepayments` (+Idempotency-Key), `/rate-changes`.
- **Rules & errors**: `OVERPAYMENT`, `SETTLE_DUE_INSTALLMENTS_FIRST`, `LOAN_NOT_ACTIVE`, `INTEREST_FREE_LOAN`; tell the
  user that loans do not change wallet balances (suggest recording a transaction).
- **Done when**: the previewed schedule matches the backend; paying everything marks the loan Paid off.

### F18. Investments

- **Screens**: Portfolio (Assets → Investments): market value, cost, unrealized gain/loss (₫, %, green/red), realized,
  total return, **allocation pie chart** by type; display currency picker · Asset list: name, symbol, quantity, value,
  gain/loss %, "No price yet" label when `priced=false` · Detail: quantity, average cost, current price + price date,
  **Update price** button, gain/loss, trade history (Buy/Sell/Dividend, cash status) · Create/Edit asset: name, symbol,
  type (Stock, Fund, Gold, Crypto, Bond, Real estate, Other), currency (on create only), note · Buy/Sell: quantity (up
  to 8 decimals), price, fee, date, wallet or "Track only"; a computed total line; selling shows the expected gain/loss
  · Dividend: amount, wallet, income category (default Interest), date.
- **API**: §12 – `/investments`, `/investments/portfolio`, `/buys`, `/sells`, `/incomes`, `/price`.
- **Rules & errors**: PENDING through `cashStatus`; `INSUFFICIENT_QUANTITY`, `FEE_TOO_HIGH`, `STALE_PRICE`,
  `EXCHANGE_RATE_MISSING` (multi-currency portfolio).
- **Done when**: buys/sells/dividends update the average cost and gains exactly as the backend does.

### F19. Reports and net worth

- **Reports screen** (from Home): period picker (This month, Last month, Quarter, Year, Custom; swipe sideways to change
  month), report currency · **Period overview**: income, expense, net, savings rate, % arrows vs the previous period ·
  **By category**: Expense/Income tabs, pie chart + list (name, amount, %), tap → sub-categories → transactions (F07
  filtered) · **Cash flow**: bar chart per month (or per day when the period is ≤ 31 days): income, expense, money moved
  to savings/investments, net cash-flow line · **Export CSV**: choose period → download → save file → share sheet.
- **Net worth screen** (top of the Assets tab + detail): big number, assets − liabilities, groups Wallets / Savings /
  Investments / Deposits / Lent / Borrowed with every item (original amount + converted).
- **Deposits & loans card** (in Assets): `/dashboard` data – items due in the next 30 days, expected cash flow for 12
  months.
- **API**: §13 – `/reports/summary`, `/categories`, `/cash-flow`, `/net-worth`, `/transactions.csv`, `/dashboard`.
- **Rules**: reports update a few hundred ms after a transaction (refresh when the screen is shown again);
  `INVALID_PERIOD`, `PERIOD_TOO_LONG`, `EXCHANGE_RATE_MISSING`.
- **Done when**: numbers match the backend; charts are readable in dark mode; the CSV opens in Excel/Numbers.

### F20. Notifications and push

- **Screens**: Notification list (bell): grouped by day, unread in bold, icon per `type`; tap → mark as read + navigate
  by `referenceType`/`referenceId` (BILL → bill, BUDGET → budget, SAVING_GOAL → goal, DEPOSIT → deposit, LOAN → loan;
  DAILY_REMINDER has no reference → open Quick add); "Mark all as read" · Notification settings (Settings →
  Notifications): a switch per type (12 types grouped by feature), daily reminder + time, quiet hours (from–to), link to
  the system settings when the permission is off.
- **Push (FCM)**: Firebase Messaging on Android + iOS (expect/actual); ask for notification permission **after the user
  creates their first budget or bill**; after sign-in and whenever the token changes: `POST /notifications/devices`
  `{token, provider:"FCM", platform, deviceName}`; on sign-out: `DELETE /notifications/devices/{token}`; Android creates
  the `default` channel; tapping a push opens the right screen (C0 deep link); in the foreground refresh the badge and
  the list.
- **API**: §14, §14.1.
- **Done when**: real pushes arrive on Android and iOS (Firebase + APNs key) and open the right screen; the badge is
  correct.

### F21. Offline and sync

- **Cache** (Room): wallets, categories, the first 3 transaction pages, budgets, bills, goals, notifications, Home; show
  the cache at once, refresh in the background, show "Updated at HH:mm".
- **Write queue**: income/expense transactions and transfers created offline are stored with their Idempotency-Key,
  labelled "Waiting to send", and sent automatically when back online (same key → no duplicate); business errors mark
  the item as failed for the user to fix.
- **Offline banner**; actions that need the network (paying bills, saving deposits, trades) are disabled offline.
- **Done when**: airplane mode on → record 3 transactions → airplane mode off → all 3 reach the server without
  duplicates.

### F22. Polish and release

- **Polish**: review loading/empty/error on every screen; dark mode; large fonts; accessibility; long-list performance;
  English strings; icon, splash, app name.
- **Release**: HTTPS production baseUrl per build type; Android signing (AAB), iOS signing; CI producing Play Internal
  Testing and TestFlight builds; `docs/RELEASE.md`; privacy policy; permission descriptions (camera, notifications,
  Face ID) in `Info.plist` and `AndroidManifest`.
- **Release checklist**:
  - [ ] Every feature F01–F21 is "done" in `docs/PLAN.md`; CI green on Android + iOS.
  - [ ] No `Double`/`Float` for money; `vi-VN` formatting correct.
  - [ ] Expired / revoked refresh token → Sign in, no loops.
  - [ ] Flaky network creates no duplicate transactions (Idempotency-Key).
  - [ ] PENDING actions show the right state and update themselves.
  - [ ] Push arrives on real devices, opens the right screen, stops after sign-out.
  - [ ] No secrets in git; every production request uses HTTPS.
  - [ ] No Google button on iOS (or Sign in with Apple is offered); in-app account deletion exists.
  - [ ] Dark mode, large fonts, VoiceOver/TalkBack read the main buttons.

---

## When the app needs more from the backend

Open a **separate Claude Code session** in the `financemanage_backend` folder and describe the need, for example:

```text
The mobile app needs GET /api/v1/home returning in one request: total wallet balance converted, this and last month's
income/expense, the 3 budgets with the highest %, bills due in the next 7 days, the closest saving goal, the 5 latest
transactions and the unread notification count. Follow the existing modular-monolith architecture, add integration
tests, update docs/MOBILE_API_GUIDE.md and docs/MOBILE_API_GUIDE.en.md, re-export docs/api/openapi.json, run
./gradlew build.
```

Once the backend is done: copy `MOBILE_API_GUIDE.en.md` and `openapi.json` again into the app's `docs/backend/` and tell
Claude (app session): "The API documents changed; compare them with the previous version using git diff and update the
app."

Known backend requests: `GET /api/v1/home` (F12), SSE `/api/v1/events` for live data (optional), `POST /auth/apple`
(only if Google sign-in is enabled on iOS).
