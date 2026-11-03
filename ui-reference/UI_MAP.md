# UI map — ManyManager Simple

Current requirements: [docs/README.md](../docs/README.md). Android and HTML share visual behavior and essential states; preview data is memory-only and resets on reload.

| Feature | Android | HTML | Behavior |
| --- | --- | --- | --- |
| Budget management | `page_budget.xml`, `BudgetManagementUi.kt`, `MainActivity.kt` | `screens/budget.js`, `components/source-dialog.js` | Period arrows, one total, Update budget; captured period |
| Payment methods | `dialog_add_source.xml`, `dialog_edit_source.xml`, `BudgetSourceDialogs.kt` | `components/source-dialog.js` | Name-only Add/Rename, stable IDs, no money entry |
| Home | `page_home.xml`, `BudgetHomeUi.kt` | `screens/home.js` | Spending, remaining = budget − spending, expandable method spending |
| Summary | `SummaryBreakdownUi.kt`, `AllExpensesUi.kt`, summary XML rows/panels | `screens/summary.js` | Category/method spending and count, inline expandable rows, All Expenses |
| Expense entry/details/edit/delete | `ExpenseSheetDialog.kt`, `ExpenseDetailsDialog.kt`, `ExpenseState.kt` | expense components, `app.js` | Required payment method, optional category/note, civil date, stable expense ID; confirmation before deletion |
| State and data | `PersistentDataViewModel.kt`, `ManyManagerRepository.kt`, Room schema 2 | `state.js`, `preview-model.js` | One budget per calculator/period, persistent Android / in-memory preview |
| Period boundaries | `BudgetPeriodCalculator.kt`, `BudgetPeriodFormatter.kt` | `budget-period.js`, `render-helpers.js` | Default 10th–9th, no carry-over, methods retained |
| Navigation/settings/categories | `MainActivity.kt`, native page/dialog XML | existing screen components and `app.js` | Five pages, history, language, light/dark, category creation and return |
| Examples/checks | repository/database/lifecycle tests, `SingleBudgetMigrationTest.kt` | `js/fixture.js`, test pages | Single budget example, zero budget, rename, edits/deletes, boundaries; Android also tests schema upgrade |

Colors, spacing and radii remain in native resources and `css/`. Translations live in native `values{-iw,-en,-ru}/strings.xml` and `locales/{he,en,ru}.json`; all relevant payment terminology is localized. RTL/LTR use existing layout-direction rules.

Legacy native `sourceId` / `budget_sources` names now identify payment methods. They carry no allocation or balance. Schema 1 is retained; registered migration 1→2 aggregates old allocations into `period_budgets` without changing historical expense references.

Ordinary Android recreation retains guarded drafts; uncertain restored submissions require review rather than replay. HTML does not simulate lifecycle/persistence. Native and browser font metrics/dialog behavior differ; pixel-level parity and manual accessibility coverage remain unverified.
