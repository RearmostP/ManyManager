# UI map

| Feature | Android source | Web implementation | Main interactions |
| --- | --- | --- | --- |
| App shell/navigation | `MainActivity.kt`, `activity_main.xml`, `AppearanceTransitionState.kt` | `index.html`, `js/app.js`, `js/state.js`, `css/base.css` | Menu, five pages, back history, overlays |
| Home/budget breakdown | `page_home.xml`, `BudgetHomeUi.kt` | `js/screens/home.js`, `css/screens/home.css` | Expand, remaining balance, expense sheet |
| Summary | `page_summary.xml`, `panel_*.xml` | `js/screens/summary.js`, `css/screens/summary.css` | Three tabs; expense totals remain empty |
| Settings | `page_settings.xml`, `MainActivity.kt` | `js/screens/settings.js`, `css/screens/settings.css` | Category navigation, language and theme expansion |
| Categories | `page_categories.xml`, `CategoryManagementUi.kt`, `BudgetViewModel.kt` | `js/screens/categories.js`, `css/screens/categories.css`, `js/components/category-dialog.js` | Back, list, add dialog |
| Budget/source list | `page_budget.xml`, `row_budget_source.xml`, `BudgetManagementUi.kt` | `js/screens/budget.js`, `css/screens/budget.css` | Month arrows, total, source rows, add |
| Source forms/model | `dialog_add_source.xml`, `dialog_edit_source.xml`, `BudgetSourceDialogs.kt`, `MonthlyBudgetAllocations.kt`, `BudgetAmounts.kt` | `js/components/source-dialog.js`, `js/preview-model.js`, `css/components.css` | Add, rename, +/− preview, cancel, confirm |
| Side menu | `dialog_menu.xml`, `SideMenuDialog.kt` | `js/components/drawer.js`, `css/components.css` | Four top-level destinations, close |
| Expense sheet | `dialog_expense.xml`, `ExpenseSheetDialog.kt` | `js/components/expense-sheet.js`, `css/components.css` | Same source identities, category/source choice, close/drag; save disabled |

Shared colors, spacing, and radii are in Android `res/values/colors.xml`, `res/values-night/colors.xml`, shape drawables, and `res/values/styles.xml`; web `css/tokens.css`, `css/base.css`, and `css/components.css`. Web rendering helpers and icon paths are in `js/render-helpers.js` and `js/icons.js`.

Translations are in Android `res/values/strings.xml`, `res/values-iw/strings.xml`, `res/values-en/strings.xml`, `res/values-ru/strings.xml`, and theme label resources; web `locales/{he,en,ru}.json` and `js/i18n.js`. Web navigation and temporary overlay state are in `js/state.js` and `js/app.js`. The in-memory source/category model is in `js/preview-model.js`; Android uses `BudgetViewModel.kt` and `MonthlyBudgetAllocations.kt`.

Maintenance: future Android UI changes should include corresponding `ui-reference/` and `UI_MAP.md` updates.

Limits: both new management models are temporary and do not survive process death/reload; expense saving is still disabled. Browser font metrics, native dialogs, IME, system bars, and ripple effects differ. Android and browser budget screens were inspected, but pixel-level equivalence is unverified.
