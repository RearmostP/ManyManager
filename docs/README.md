# ManyManager — current application

This is the authoritative development reference for ManyManager on `main`. Confirmed current requirements supersede historical plans. The Android application is a local budget and expense manager using XML/Views and AppCompat. `:androidApp` owns production persistence; `:shared` retains a Compose sample, not the business data layer. The UI covers Home, Summary, Budget Management, Expense Categories and Settings.

## Financial rules and periods

- Money is exact integer `Long` minor units (agorot); the current UI uses ILS. Input accepts decimal points or commas and at most two fractional digits. No floating-point money or silent rounding.
- Expenses must be positive, with a civil date, required source, optional category and optional note. `categoryId=null` means localized “Uncategorized”, not a seeded category. Referenced entities must belong to the same calculator.
- Each period has one independent optional overall budget and optional per-source spending limits. Sources persist across periods and may be used without a limit. Limits do not reserve money or contribute to the overall budget. Spending is allowed without either threshold and beyond both; overages are labelled. Unset differs from explicitly configured zero. New user-entered thresholds are nonnegative; migrated signed historical amounts remain unchanged. See [the budget model and migration](budget-model.md).
- Categories, sources and expenses have stable UUID identities scoped by `calculatorId`; names and list positions are not identities. Duplicate names are allowed and groups remain separate. Source renaming preserves references. The UI supports category creation, not category renaming or an icon selector.
- One local calculator is initialized idempotently, with start day 10 and currency ILS. User categories, sources and expenses start empty. There is no calculator-switching, currency or start-day settings UI.
- `BudgetPeriodCalculator` owns monthly civil-date boundaries `[start, endExclusive)`, defaulting to the 10th through the 9th. Dates are stored as epoch days, not UTC-midnight timestamps. Only “today” depends on the device clock/zone. Supported start days are 1–28.
- Expense date determines its period, including when editing moves it across a boundary. A new period starts with no overall budget and no source limits; neither is copied forward. Previous periods retain their own saved amounts.
- Home and Summary follow the active period independently of historical Budget Management selection. Entering Budget Management selects the active period; an open budget form retains its captured period. Resume/date/time/zone refreshes update active data; no background rollover job is installed.

## Storage and ownership

Room schema 2 stores six entities in app-private `databases/many-manager.db`: calculators, categories, expense sources, period source limits (legacy `budget_allocations` table), expenses and independent `period_budgets`. Composite foreign keys enforce ownership and prevent orphan references. Registered migration 1→2 preserves all old tables/rows and initializes each represented period's overall budget from its exact allocation sum. No other periods are created. Zero and raw negative legacy amounts are retained. Positive expense validation remains a repository rule.

`ManyManagerApplication` owns Room and the process repository and initializes the local calculator off the main thread. Business reads and writes go through `ManyManagerRepository`; only calculator initialization calls the DAO directly outside it. The repository reloads saved calculator settings, validates IDs/amounts/periods, performs transactions and emits observed results. Success follows commit; storage errors and overflow roll back writes.

`PersistentDataViewModel` controls active and selected-period streams, drafts, previews, loading/error/retry and guarded actions. Views submit actions and render state, without a parallel financial model. `BudgetViewModel` retains only expansion UI state.

Overall budgets, source limits and expenses are the facts. `BudgetCalculations` and `BudgetSnapshot` derive totals, counts, category/source breakdowns and remaining amounts; overall remaining = overall budget − all spending, source remaining = source limit − source spending. Nullable configured amounts distinguish unset from zero. Intermediate sums use `BigInteger`; exposed results must fit `Long`. The legacy `totalAllocatedMinor` snapshot name now represents the independent overall budget (zero numeric fallback when unset); UI uses `overallBudgetMinor` to detect unset. There is no independently mutable balance table. Expense updates validate both old and new periods before commit; deletion validates the affected period. Source creation plus optional initial limit and source rename plus limit changes are atomic.

Home and the three Summary tabs consume the same transactional financial snapshot. Independent management/choice streams may converge at different times; there is no promise of atomic rendering across all streams.

## Expense and Summary interaction

Creation saves only after validation and commit. Categories appear as full-width inline rows with a checkmark on the selected row; the list uses the form's normal scrolling. Editing uses a separate wrapping category picker: tapping confirms a draft selection, while Back/Cancel leaves every draft field unchanged. Categories remain flat and optional on main; Uncategorized is selectable even when no user categories exist. Creating a category from an expense draft preserves amount/date/source/note, selecting the new stable ID only after success. Cancel returns to the unchanged draft.

Budget dialogs retain rounded surfaces, blue primary actions, localized cancellation and captured periods. Overall budget editing is at the top of Budget Management; source editing uses an absolute optional spending limit, with blank/remove clearing that period's limit. Source creation may leave the limit blank. Home shows overall spending/remaining or “Budget not set”; its expanded panel shows source spending and remaining/overage only when a limit exists. Views render controller/domain values.

Tapping an expense opens read-only details first. Edit replaces values with controls in the same window for amount, date, category, source and note. Save updates the same expense ID and returns to saved details; Cancel/Back discards the edit draft. Delete is available while editing and requires confirmation identifying the saved expense. Cancelling confirmation retains the draft; successful deletion closes details and returns to the list. Known failures retain input for explicit retry; simultaneous/repeated submissions are guarded.

Summary contains All Expenses, Categories and Sources for the active period. All Expenses is ungrouped, newest date first with an ID tie-breaker. Category/source groups show spending and count, sorted by descending spending then stable ID (null category first on ties). Sources with expenses appear even without allocations.

Each group expands expense rows inside one unified rounded card in the same tab, using normal page scrolling. Multiple groups can remain expanded; there is no intermediate group screen. Rows emphasize amount, contextual secondary information and a two-line note preview, omitting the repeated grouping field. Details open above the list; tab, expanded group IDs and scroll survive closing and ordinary configuration changes.

## Language, appearance and design reference

Hebrew (default), Russian and English support RTL/LTR and light/dark themes. Appearance stays in SharedPreferences; locales use AppCompat/platform storage, outside Room.

[ui-reference](../ui-reference/README.md) is a lightweight visual playground for trying designs before Android implementation. Run `python preview.py`; a fixed fictional fixture (3 categories, 7 expenses, 2 sources) resets on reload. Keep Android and HTML visually aligned, including essential interaction states, while keeping HTML lightweight. Do not reproduce production business logic, persistence or lifecycle guarantees. Verify the affected screen/interaction when changing the preview; comprehensive browser business-logic regression suites are not required for every Android change.

## Limits and maintenance

There are no refunds/nonpositive expenses, historical Summary navigation, multiple-calculator UI, accounts, cloud sync, export or database encryption. Local business operations do not require a network. Changing period start day needs a historical-data policy before exposing it; days 29–31 and multi-currency behavior need explicit decisions.

Keep Android backup/device-transfer eligibility: `allowBackup=true`, with no database exclusion. App-private storage is not encryption or a guaranteed backup. Actual backup/restore and device transfer remain unverified.

Ordinary Activity recreation and Bundle restoration are not durable draft recovery or exactly-once writes across arbitrary process death. Restored uncertain submissions are not replayed; users must review saved data before a deliberate new operation. API 30 runtime, full manual TalkBack/font-scale/keyboard coverage, true midnight/zone changes, process death during commit and Android/HTML pixel parity remain verification gaps; historical test counts are not fresh verification.

Future schema changes require a version increment, retained exported schemas, a real registered migration and tests preserving populated old files, IDs, relationships, money, dates and settings. Do not delete user databases or add destructive fallback to resolve upgrade failures.

For development checks from the repository root with a compatible JDK:

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:lintDebug
.\gradlew.bat :androidApp:assembleDebugAndroidTest
.\gradlew.bat :androidApp:connectedDebugAndroidTest
```

Before instrumentation inspect `leaveApksInstalledAfterRun=true`, the isolated test Application/runner, UUID test databases, test preference prefixes and installed packages. Never clear/uninstall user data as routine verification. Check actual test counts and runner reports, not just Gradle's exit code.

## Implementation map

Paths are relative to the repository root.

| Responsibility | Files |
| --- | --- |
| Application ownership | [ManyManagerApplication.kt](../androidApp/src/main/kotlin/com/example/manymanager/ManyManagerApplication.kt) |
| Business operations and observation | [ManyManagerRepository.kt](../androidApp/src/main/kotlin/com/example/manymanager/data/ManyManagerRepository.kt) |
| Room and relationships | [ManyManagerDatabase.kt](../androidApp/src/main/kotlin/com/example/manymanager/data/local/ManyManagerDatabase.kt), [Entities.kt](../androidApp/src/main/kotlin/com/example/manymanager/data/local/Entities.kt), [ManyManagerDao.kt](../androidApp/src/main/kotlin/com/example/manymanager/data/local/ManyManagerDao.kt), [schema 1](../androidApp/schemas/com.example.manymanager.data.local.ManyManagerDatabase/1.json), [schema 2](../androidApp/schemas/com.example.manymanager.data.local.ManyManagerDatabase/2.json) |
| Money and periods | [BudgetAmounts.kt](../androidApp/src/main/kotlin/com/example/manymanager/BudgetAmounts.kt), [BudgetCalculations.kt](../androidApp/src/main/kotlin/com/example/manymanager/domain/BudgetCalculations.kt), [BudgetPeriodCalculator.kt](../androidApp/src/main/kotlin/com/example/manymanager/domain/BudgetPeriodCalculator.kt), [Models.kt](../androidApp/src/main/kotlin/com/example/manymanager/domain/Models.kt) |
| State and UI wiring | [PersistentDataViewModel.kt](../androidApp/src/main/kotlin/com/example/manymanager/PersistentDataViewModel.kt), [MainActivity.kt](../androidApp/src/main/kotlin/com/example/manymanager/MainActivity.kt) |
| Expense flows | [ExpenseSheetDialog.kt](../androidApp/src/main/kotlin/com/example/manymanager/ExpenseSheetDialog.kt), [ExpenseDetailsDialog.kt](../androidApp/src/main/kotlin/com/example/manymanager/ExpenseDetailsDialog.kt), [ExpenseState.kt](../androidApp/src/main/kotlin/com/example/manymanager/ExpenseState.kt) |
| Summary rendering | [SummaryBreakdownUi.kt](../androidApp/src/main/kotlin/com/example/manymanager/SummaryBreakdownUi.kt), [AllExpensesUi.kt](../androidApp/src/main/kotlin/com/example/manymanager/AllExpensesUi.kt), [UI map](../ui-reference/UI_MAP.md) |

## Branch-local presentation parity

The [presentation port record](presentation-port.md) documents the reviewed original commit, preserved icon/identity/version baseline, selective transfers and fresh verification. main now has an independent period budget with optional source limits, flat optional categories and Room schema 2; original's model and Room schema 3 remain branch-specific. This implementation does not adopt original's schema, migrations or category validation requirements. Both applications have separate package identities and app-private data.

## Release and historical records

Use the [current release guide](release.md) for building, signing, verification and independent branch releases.

Historical records retain old scope exclusions, proposals and test logs; they are not current requirements:
[persistence plan](archive/persistent-data-plan.md), [acceptance report](archive/persistent-data-acceptance.md), [expense/details and unified-card delivery](archive/expense-details.md), [release preparation records](archive/release-preparation.md).
