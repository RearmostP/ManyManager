# ManyManager Simple — current application

This document describes `original`. `main` belongs to the personal edition; do not infer its behavior here. ManyManager Simple uses native Android XML/Views and AppCompat. `:androidApp` owns production data; `:shared` retains a Compose sample.

## Single budget and payment methods

- Each calculator and budget period has one directly stored non-negative budget in exact integer `Long` minor units (agorot). There is no hidden budget source.
- Remaining = period budget − every expense dated within that period. A ₪20,000 budget with ₪100 cash and ₪250 card spending leaves ₪19,650. The cash/card breakdown shows ₪100/₪250 and counts; methods have no allocations or balances.
- Payment methods are name-only records, available across periods. Creation and renaming never request or modify money. Stable UUID identities survive renaming; duplicate names remain separate.
- Each expense requires a method, a positive exact amount and a civil date; category and note remain optional. Null category means localized Uncategorized. Every reference belongs to the same calculator. Zero budgets and negative remaining balances are allowed.
- The existing `BudgetPeriodCalculator` defines civil-date intervals `[start, endExclusive)`: by default the 10th through the 9th. Dates are epoch days, not UTC-midnight timestamps. Supported start days remain 1–28.
- New periods start at zero budget, with no copying or carry-over. Methods remain available. Moving an expense across a period boundary recalculates both periods.
- One local calculator initializes idempotently with start day 10 and ILS; business data starts empty. There is no calculator, currency or period-start settings UI.
- Decimal point/comma input accepts at most two fractional digits, with no floating-point money or silent rounding. Aggregations use `BigInteger`; final exposed totals must fit `Long`. Invalid totals roll back writes.

## Storage and migration

Room schema **2** stores calculators, categories, payment methods (legacy table name `budget_sources`), `period_budgets` and expenses in app-private `databases/many-manager.db`. `period_budgets` is keyed by calculator and period start and references the calculator directly. Legacy source class/field names remain internal identifiers for payment methods, including expense `sourceId`.

Registered `MIGRATION_1_2` sums each old calculator/period’s source allocations into one budget, using exact Kotlin aggregation rather than SQLite SUM. It preserves all source IDs, including unallocated sources, expenses, references, categories, civil dates, notes and calculator settings. Negative legacy allocations or totals outside `Long` abort migration transactionally; data is never reset. Both exported schemas are retained. Future changes require a version increment and tested migration; destructive fallback is forbidden.

`ManyManagerApplication` owns Room and the repository. `ManyManagerRepository` owns validation, transactions, observations and mutations. `setPeriodBudget` replaces a selected period’s amount; `createPaymentMethod` and `renameSource` only change method records. Expense create/update/delete validate resulting snapshots before commit, including both periods for moves. Budget, category and method spending are computed in `BudgetCalculations`; no mutable balance table exists.

`PersistentDataViewModel` owns active/selected-period streams and guarded form drafts. Success follows commit, known failures preserve input for retry, and pending writes cannot submit twice. Home/Summary consume the same transactional active snapshot. Management and choices observe independently and converge on saved facts.

## Screens

Budget Management retains period arrows and shows one total with an Update budget action. Its separate Payment methods section lists name-only rows with Add and Rename actions. The budget form captures the selected period; rollover does not redirect its save.

Home shows active-period spending, remaining and total budget. Expanding shows spending by payment method. Summary retains All Expenses, Categories and Payment Methods with spending, counts and expandable inline expense rows in unified rounded cards. Methods without expenses are omitted from spending summaries.

Expense creation, read-only details, full in-place editing, confirmed deletion, category creation/return, civil-date picker and optional note behavior remain. Save edits the same ID and returns to saved details. Cancel/Back discards edits; deletion requires confirmation. Closing details preserves Summary tab, expansion and scroll.

Hebrew (default RTL), Russian and English (LTR), and light/dark appearance remain supported. Appearance preferences stay outside Room. Simple’s applicationId and Android namespace are `com.example.simple.manymanager`. The personal edition retains `com.example.manymanager`. The packages install separately and do not automatically share or transfer app-private data. Version and signing are unchanged. Exported Room schemas are also retained under the renamed database class’s schema directory for migration tests.

## Lifecycle and limits

Home/Summary follow the active period independently of historical management selection. Entering management selects the active period. Resume and date/time/zone changes refresh data; no background rollover job exists.

ViewModel retains pending forms across ordinary recreation. Restored Bundle forms with uncertain submissions are locked against replay; close, inspect saved data and start a deliberate new operation. Bundle recovery is not durable draft recovery or exactly-once storage across arbitrary process death.

No refunds/nonpositive expenses, cloud sync, accounts, multiple-calculator UI, historical Summary navigation, export or database encryption. Keep `allowBackup=true` without database exclusions. Actual backup/device transfer, API 30 runtime, manual TalkBack/large-font/physical-keyboard coverage and arbitrary process death during commit remain unverified.

## Development and visual reference

Keep Android and [ui-reference](../ui-reference/README.md) visually aligned, including essential states. Preview data stays in memory and resets on reload; do not reproduce production persistence/lifecycle. Fictional examples belong in `ui-reference/js/fixture.js`. Verify affected interactions.

With a compatible JDK, or Android Studio’s Gradle runner:

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:lintDebug
.\gradlew.bat :androidApp:assembleDebugAndroidTest
.\gradlew.bat :androidApp:connectedDebugAndroidTest
```

Before instrumentation inspect `leaveApksInstalledAfterRun=true`, the isolated Application/runner, UUID test database names, test preferences and installed packages. Never clear/uninstall production data. Check real runner counts.

Focused checks cover the ₪20,000 example, zero budget, method rename, edits/deletes and boundary moves; schema migration tests populate multiple calculators/methods/periods, preserve historical references and reject overflow without reset. See the task report for actual run results.

## Verification of the single-budget change

Android Studio Gradle passed debug assembly, lint, 32 JVM tests and test APK assembly. The connected Android device run passed 66 instrumentation tests, including populated schema 1→2 migration, exact maximum/overflow rollback and real Activity/dialog integration.

Chrome headless passed HTML checks: single budget model 14, expense forms 32, summary 35, expense details 65, unified cards 37 and single-budget UI 11. A 320px Hebrew budget screen screenshot was inspected. These results do not establish pixel parity, manual accessibility, API 30 runtime or arbitrary process-death behavior.

## Implementation map

- Storage: `data/local/Entities.kt`, `ManyManagerDao.kt`, `ManyManagerDatabase.kt`, exported schemas 1 and 2.
- Rules: `data/ManyManagerRepository.kt`, `domain/BudgetCalculations.kt`, `Models.kt`, `BudgetPeriodCalculator.kt`, `BudgetAmounts.kt`.
- UI/state: `PersistentDataViewModel.kt`, `MainActivity.kt`, `BudgetManagementUi.kt`, `BudgetSourceDialogs.kt`, `BudgetHomeUi.kt`, `SummaryBreakdownUi.kt`, expense dialogs.
- Tests: `ManyManagerRepositoryTest`, `ManyManagerDatabaseTest`, `SingleBudgetMigrationTest`, `BudgetLifecycleTest`, JVM domain tests, HTML test pages.

Use [release.md](release.md) for release tasks. Historical documents in `docs/archive/` remain unchanged and are not current requirements.
