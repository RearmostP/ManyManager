# ManyManager Simple — current application

This is the authoritative development reference for ManyManager Simple on `original`. It describes the implementation currently on this branch; a Simple-specific budget model and a separate package identity have not been implemented. Confirmed current requirements supersede historical plans. The Android application is a local budget and expense manager using XML/Views and AppCompat. `:androidApp` owns production persistence; `:shared` retains a Compose sample, not the business data layer. The UI covers Home, Summary, Budget Management, Expense Categories and Settings.

## Financial rules and periods

- Money is exact integer `Long` minor units (agorot); the current UI uses ILS. Input accepts decimal points or commas and at most two fractional digits. No floating-point money or silent rounding.
- Expenses must be positive, with a civil date, required source, optional category and optional note. `categoryId=null` means localized “Uncategorized”, not a seeded category. Referenced entities must belong to the same calculator.
- A budget source is also an expense source. Allocations are per source and period, cannot be negative, and may be zero. Spending is allowed without an allocation and may make the remaining balance negative. Budget adjustments change allocations, never create expenses.
- Categories, sources and expenses have stable UUID identities scoped by `calculatorId`; names and list positions are not identities. Duplicate names are allowed and groups remain separate. Source renaming preserves references. The UI supports category creation, not category renaming or an icon selector.
- One local calculator is initialized idempotently, with start day 10 and currency ILS. User categories, sources and expenses start empty. There is no calculator-switching, currency or start-day settings UI.
- `BudgetPeriodCalculator` owns monthly civil-date boundaries `[start, endExclusive)`, defaulting to the 10th through the 9th. Dates are stored as epoch days, not UTC-midnight timestamps. Only “today” depends on the device clock/zone. Supported start days are 1–28.
- Expense date determines its period, including when editing moves it across a boundary. Missing allocation means zero; a new period does not copy budgets or carry balances.
- Home and Summary follow the active period independently of historical Budget Management selection. Entering Budget Management selects the active period; an open budget form retains its captured period. Resume/date/time/zone refreshes update active data; no background rollover job is installed.

## Storage and ownership

Room schema 1 stores five entities in app-private `databases/many-manager.db`: calculators, categories, budget sources, period allocations and expenses. Composite foreign keys enforce ownership and prevent orphan references. Positive expense validation is a repository rule; schema 1 does not itself enforce the amount sign.

`ManyManagerApplication` owns Room and the process repository and initializes the local calculator off the main thread. Business reads and writes go through `ManyManagerRepository`; only calculator initialization calls the DAO directly outside it. The repository reloads saved calculator settings, validates IDs/amounts/periods, performs transactions and emits observed results. Success follows commit; storage errors and overflow roll back writes.

`PersistentDataViewModel` controls active and selected-period streams, drafts, previews, loading/error/retry and guarded actions. Views submit actions and render state, without a parallel financial model. `BudgetViewModel` retains only expansion UI state.

Allocations and expenses are the facts. `BudgetCalculations` and `BudgetSnapshot` derive totals, counts, category/source breakdowns and balances; remaining = allocated − spent. Intermediate sums use `BigInteger`; exposed results must fit `Long`. There is no independently mutable balance table. Expense updates validate both old and new periods before commit; deletion validates the affected period. Source creation plus initial allocation and source rename plus allocation adjustment are atomic.

Home and the three Summary tabs consume the same transactional financial snapshot. Independent management/choice streams may converge at different times; there is no promise of atomic rendering across all streams.

## Expense and Summary interaction

Creation saves only after validation and commit. Creating a category from an expense draft preserves amount/date/source/note, selecting the new stable ID only after success. Cancel returns to the unchanged draft.

Tapping an expense opens read-only details first. Edit replaces values with controls in the same window for amount, date, category, source and note. Save updates the same expense ID and returns to saved details; Cancel/Back discards the edit draft. Delete is available while editing and requires confirmation identifying the saved expense. Cancelling confirmation retains the draft; successful deletion closes details and returns to the list. Known failures retain input for explicit retry; simultaneous/repeated submissions are guarded.

Summary contains All Expenses, Categories and Sources for the active period. All Expenses is ungrouped, newest date first with an ID tie-breaker. Category/source groups show spending and count, sorted by descending spending then stable ID (null category first on ties). Sources with expenses appear even without allocations.

Each group expands expense rows inside one unified rounded card in the same tab, using normal page scrolling. Multiple groups can remain expanded; there is no intermediate group screen. Rows emphasize amount, contextual secondary information and a two-line note preview, omitting the repeated grouping field. Details open above the list; tab, expanded group IDs and scroll survive closing and ordinary configuration changes.

## Language, appearance and design reference

Hebrew (default), Russian and English support RTL/LTR and light/dark themes. Appearance stays in SharedPreferences; locales use AppCompat/platform storage, outside Room.

[ui-reference](../ui-reference/README.md) is a lightweight design reference. Keep its appearance and essential interactions aligned with Android so future design experiments start from an accurate visual baseline. It is not a second production application: preview data is isolated JS memory and resets on reload. Simplifying duplicated logic, implementation and testing burden is a separate task. Preserve visual synchronization and existing tests meanwhile.

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
| Room and relationships | [ManyManagerDatabase.kt](../androidApp/src/main/kotlin/com/example/manymanager/data/local/ManyManagerDatabase.kt), [Entities.kt](../androidApp/src/main/kotlin/com/example/manymanager/data/local/Entities.kt), [ManyManagerDao.kt](../androidApp/src/main/kotlin/com/example/manymanager/data/local/ManyManagerDao.kt), [schema 1](../androidApp/schemas/com.example.manymanager.data.local.ManyManagerDatabase/1.json) |
| Money and periods | [BudgetAmounts.kt](../androidApp/src/main/kotlin/com/example/manymanager/BudgetAmounts.kt), [BudgetCalculations.kt](../androidApp/src/main/kotlin/com/example/manymanager/domain/BudgetCalculations.kt), [BudgetPeriodCalculator.kt](../androidApp/src/main/kotlin/com/example/manymanager/domain/BudgetPeriodCalculator.kt), [Models.kt](../androidApp/src/main/kotlin/com/example/manymanager/domain/Models.kt) |
| State and UI wiring | [PersistentDataViewModel.kt](../androidApp/src/main/kotlin/com/example/manymanager/PersistentDataViewModel.kt), [MainActivity.kt](../androidApp/src/main/kotlin/com/example/manymanager/MainActivity.kt) |
| Expense flows | [ExpenseSheetDialog.kt](../androidApp/src/main/kotlin/com/example/manymanager/ExpenseSheetDialog.kt), [ExpenseDetailsDialog.kt](../androidApp/src/main/kotlin/com/example/manymanager/ExpenseDetailsDialog.kt), [ExpenseState.kt](../androidApp/src/main/kotlin/com/example/manymanager/ExpenseState.kt) |
| Summary rendering | [SummaryBreakdownUi.kt](../androidApp/src/main/kotlin/com/example/manymanager/SummaryBreakdownUi.kt), [AllExpensesUi.kt](../androidApp/src/main/kotlin/com/example/manymanager/AllExpensesUi.kt), [UI map](../ui-reference/UI_MAP.md) |

## Release and historical records

Use the [current release guide](release.md) for building, signing, verification and independent branch releases.

Historical records retain old scope exclusions, proposals and test logs; they are not current requirements:
[persistence plan](archive/persistent-data-plan.md), [acceptance report](archive/persistent-data-acceptance.md), [expense/details and unified-card delivery](archive/expense-details.md), [release preparation records](archive/release-preparation.md).
