# Independent period budget and optional source limits

The active main model uses one independently editable overall budget per period and optional spending limits per expense source. Periods remain civil dates from the 10th (inclusive) through the 9th of the next month. Expense date selects the period, including after an edit.

A new period has neither an overall budget nor source limits. Sources themselves remain available. No budget, limit, balance or remainder is copied forward. Navigating to an older period reads its own saved amounts.

An absent row means unset. A saved zero is an explicitly configured zero threshold. All expenses count toward overall spending and their source spending. Limits do not reserve money, contribute to the overall budget or prevent expense saving. Unset overall budgets display “Budget not set”; unlimited sources display spending without a source balance. Negative remaining amounts display a labelled overage.

For example, overall 500,000 agorot, cash limit 80,000 and unlimited credit, with cash spending 20,000 and credit spending 30,000, gives overall remaining 450,000 and cash remaining 60,000. Changing or removing the cash limit leaves the overall budget at 500,000.

## Persistence and migration

Room schema 2 adds `period_budgets(calculatorId, periodStartEpochDay, amountMinor)`. Existing `budget_allocations` rows are retained, with their existing keys and integer amounts, as per-source/period spending limits. The legacy table/column names are storage compatibility details.

Registered `MIGRATION_1_2` creates one overall budget for each calculator/period represented in legacy allocations, equal to the exact sum of that period's rows. No other periods receive a budget or limit. Existing allocation rows are not rewritten, so zero and even raw negative historical amounts remain intact. Schema 1 allowed negative raw allocations, although the old repository rejected creating/reading them. Schema 2 reads their signed historical meaning; new user-entered budgets and limits must be nonnegative.

Migration sums use BigInteger and require a representable Long final total. An unrepresentable legacy total fails and rolls back the migration, preserving the old file instead of clamping, rounding, resetting or silently discarding values. No destructive fallback is configured.

Expenses, IDs, ownership, categories, sources, dates, notes and calculator settings stay in their existing tables. Locale and appearance preferences remain outside Room and are untouched.

`ManyManagerRepository` commits edits transactionally and observes period budgets as well as limits and expenses. `BudgetCalculations` prepares exact spending and remaining totals. Expense add/edit/move/delete validates affected snapshots, including both periods when dates move. Limits are independent: their sum need not fit Long.

## Interface and preview

Budget Management places the overall budget and its edit action first, followed by source spending and optional limits. Source creation starts with a blank optional limit. The editor accepts an absolute amount; blank or Remove removes that period's threshold. Captured-period drafts survive Activity recreation; an uncertain restored submission cannot be replayed.

Home keeps overall spending and remaining, and its expanded breakdown shows source spending plus remaining/overage only for configured limits. Hebrew, English and Russian share these distinctions, RTL/LTR and existing light/dark resources.

Run `python preview.py`. Fictional data remains in `ui-reference/js/fixture.js`, with an overall budget and one limited/one unlimited source. Reload resets examples. `tests/budget-model.html` checks the affected preview interactions and 320px locale/theme layouts; it is not a production persistence implementation.

## Verification

Fresh checks on 10 November 2026:

- Passed `:androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebugAndroidTest` with Android Studio's bundled JDK. The five JVM suites report **37 tests, zero failures/errors/skips**: money (6), calculations (8), periods (16 + 3), registered migration aggregation (4).
- The four migration JVM tests execute the actual registered migration with a recorded SQL/cursor boundary: multiple calculators/periods, explicit zero, signed historical totals, empty data, exact intermediate sums and unrepresentable final totals. They do not replace real SQLite schema validation.
- Added/compiled populated schema-1→2 instrumentation fixtures that reopen and validate Room schema 2 while checking expenses, IDs, dates, notes, settings, zero and negative allocations across periods. Added/compiled repository checks for the 9th/10th boundary, no carryover, independent limit removal/editing, zero versus unset, saving beyond/unset thresholds, observers and expense source/date moves/deletion. Updated UI recreation/failure fixtures for the absolute optional-limit editor.
- No connected Android device was available (`adb devices` empty), and no emulator executable was present in the configured SDK. **Instrumentation, real SQLite migration validation and Android runtime UI checks were not executed.** No APK was installed, no app was cleared/uninstalled, and installed user data was not opened.
- The preview was launched with the unchanged `python preview.py`. Edge headless passed the focused budget interaction/layout check (66 assertions, three locales × light/dark at 320px); the Hebrew overall-budget dialog and expanded Home screenshots were visually inspected (Home run: 69 assertions). The existing presentation smoke check was updated to expect the new optional-limit controls and passed all 160 assertions in a fresh browser profile.
- `git diff --check` passed. Icon, applicationId, signing configuration, versionCode and versionName are unchanged.

### Follow-up verification on a physical device

On 10 November 2026, tested commit `6eb6ba7391638a8a77501576c605246d28113d41` on a Samsung SM-S928B running Android 17 / API 37. This completes the previously pending device verification above.

- Built an isolated installation using a local ignored Gradle init script under `build/`: application `com.example.manymanager.budgetdevicetest`, instrumentation `com.example.manymanager.budgetdevicetest.test`. Production build configuration, applicationId, signing, icon and versions were unchanged.
- Ran `:androidApp:connectedDebugAndroidTest` with that init script and the connected device selected. The XML runner report confirms **76 tests, zero failures, errors or skips**: 13 database tests, 44 repository/controller tests and 19 Activity/dialog tests.
- Real Room/SQLite schema-1→2 migration and schema validation passed with populated multi-period fixtures, including explicit zero, signed negative historical amounts, preserved expenses/IDs/dates/notes and calculator settings. File close/reopen checks passed.
- Passed independent overall budgets and optional source limits, no automatic carryover, the 9th/10th boundary, unset versus zero, saving without thresholds or beyond them, limit editing/removal, source/date moves, deletion and observer recalculation. UI checks passed recreation, guarded submissions, storage-failure recovery, localized light/dark layouts and expense flows.
- An additional manual visual check confirmed Hebrew Home's “Budget not set” state, the new overall-budget dialog, saving ₪5,000, an unset next period, and restoration of the saved ₪5,000 when navigating back.
- The installed normal package `com.example.manymanager` was neither replaced nor cleared/uninstalled. Its package version and last-update timestamp were unchanged before/after the test installation. Test databases/preferences used the existing isolated test Application, in-memory or UUID-named databases and test-prefixed preferences. Fictional manual data was entered only into the isolated package.
- Remaining scope limits: this run covers API 37 on one physical device, not API 30, exhaustive accessibility/font-size/keyboard combinations, true midnight transitions or arbitrary process death during commit.

The generated local runner report is `androidApp/build/outputs/androidTest-results/connected/debug/TEST-SM-S928B - 17.xml`. Test APKs were left installed; no release, tag or APK upload was performed.
