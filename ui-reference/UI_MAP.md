# UI map

| Feature | Android source | Web implementation | Main interactions |
| --- | --- | --- | --- |
| App shell/navigation | `MainActivity.kt`, `activity_main.xml`, `AppearanceTransitionState.kt` | `index.html`, `js/app.js`, `js/state.js`, `css/base.css` | Menu, five pages, back history, overlays |
| Home/budget breakdown | `page_home.xml`, `BudgetHomeUi.kt`, `PersistentDataViewModel.kt` (activeBudget) | `js/screens/home.js`, `css/screens/home.css` | Expand, remaining balance, expense sheet |
| Summary | `page_summary.xml`, `panel_*.xml` | `js/screens/summary.js`, `css/screens/summary.css` | Three tabs; expense totals remain empty |
| Settings | `page_settings.xml`, `MainActivity.kt` | `js/screens/settings.js`, `css/screens/settings.css` | Language and theme expansion |
| Categories | `page_categories.xml`, `CategoryManagementUi.kt`, `PersistentDataViewModel.kt`, `ManyManagerRepository.kt` | `js/screens/categories.js`, `css/screens/categories.css`, `js/components/category-dialog.js` | Side menu entry, previous-page back, shared list and add dialog with stable IDs |
| Budget/source list | `page_budget.xml`, `row_budget_source.xml`, `BudgetManagementUi.kt`, `PersistentDataViewModel.kt`, `ManyManagerRepository.kt` | `js/screens/budget.js`, `css/screens/budget.css` | Period arrows and date-range label, prepared total, stable source rows, add; loading/error/retry |
| Source forms/model | `dialog_add_source.xml`, `dialog_edit_source.xml`, `BudgetSourceDialogs.kt`, `BudgetAmounts.kt`, `PersistentDataViewModel.kt` | `js/components/source-dialog.js`, `js/preview-model.js`, `css/components.css` | Atomic add + allocation, rename + adjustment, latest-input +/− preview, cancel, guarded confirm |
| Period/model verification | `data/ManyManagerRepositoryTest.kt`, domain unit tests | `tests/stage6.html`, `js/budget-period.js` | Reopen, atomic writes, periods, source/draft/category regressions |
| Side menu | `dialog_menu.xml`, `SideMenuDialog.kt` | `js/components/drawer.js`, `css/components.css` | Five destinations including Expense Categories, close |
| Expense sheet | `dialog_expense.xml`, `ExpenseSheetDialog.kt` | `js/components/expense-sheet.js`, `css/components.css` | Shared category/source choices, New category, preserved draft on confirm/cancel/Back, close/drag; save disabled |

Shared colors, spacing, and radii are in Android `res/values/colors.xml`, `res/values-night/colors.xml`, shape drawables, and `res/values/styles.xml`; web `css/tokens.css`, `css/base.css`, and `css/components.css`. Web rendering helpers and icon paths are in `js/render-helpers.js` and `js/icons.js`.

Translations are in Android `res/values/strings.xml`, `res/values-iw/strings.xml`, `res/values-en/strings.xml`, `res/values-ru/strings.xml`, and theme label resources; web `locales/{he,en,ru}.json` and `js/i18n.js`. Web navigation and temporary overlay state are in `js/state.js` and `js/app.js`. The in-memory source/category model is in `js/preview-model.js`; Android uses Application.repository → `PersistentDataViewModel.kt` → `SourceListState.kt` / prepared snapshots. `BudgetViewModel.kt` retains only expansion UI state; the temporary allocation model has been removed. Period boundaries are in Android `domain/BudgetPeriodCalculator.kt`, localized labels in `BudgetPeriodFormatter.kt`, and browser civil-date logic in `js/budget-period.js`.

Maintenance: future Android UI changes should include corresponding `ui-reference/` and `UI_MAP.md` updates.

Limits: Android categories, budget sources, and period allocations persist in Room SQLite across process restarts; browser preview data remains in JS memory and resets on reload. Android form operations survive ordinary configuration changes in the ViewModel; Bundle restoration never retries a possibly committed request automatically. Persistent source/allocation facts survive process death. Browser data resets on reload. The Android expense draft is restored across configuration changes, including while its category dialog is open. Expense saving is still disabled. Browser font metrics, native dialogs, IME, system bars, and ripple effects differ; pixel-level equivalence is unverified.
