# UI map

| Feature | Android source | Web implementation | Interactions |
| --- | --- | --- | --- |
| App shell and navigation | `MainActivity.kt`, `activity_main.xml`, `AppearanceTransitionState.kt` | `index.html`, `js/app.js`, `js/state.js`, `css/base.css` | Menu, page history, Escape/back, scrolling, overlay input blocking |
| Home and budget breakdown | `page_home.xml`, `BudgetHomeUi.kt`, `BudgetViewModel.kt` | `js/screens/home.js`, `css/screens/home.css` | Expand remaining budget, open expense sheet |
| Expense summary | `page_summary.xml`, `panel_categories.xml`, `panel_sources.xml`, `panel_all.xml`, `MainActivity.kt` | `js/screens/summary.js`, `css/screens/summary.css` | Three tabs; month controls disabled |
| Budget management | `page_budget.xml`, `BudgetManagementUi.kt`, `MonthlyBudgetAllocations.kt` | `js/screens/budget.js`, `css/screens/budget.css` | Month arrows, manage sources link |
| Settings | `page_settings.xml`, `MainActivity.kt` | `js/screens/settings.js`, `css/screens/settings.css` | Six expandable rows, language and theme chips; disabled add buttons |
| Side menu | `dialog_menu.xml`, `SideMenuDialog.kt` | `js/components/drawer.js`, `css/components.css` | Navigate to four pages, close, backdrop dismissal |
| Expense sheet | `dialog_expense.xml`, `ExpenseSheetDialog.kt` | `js/components/expense-sheet.js`, `js/app.js`, `css/components.css` | Input fields, close, backdrop, captured downward drag; save disabled |

Shared colors, spacing and radii: `res/values/colors.xml`, `res/values-night/colors.xml`, drawable shapes and `res/values/styles.xml` map to `css/tokens.css`, `css/base.css`, and `css/components.css`. Shared rendering helpers are in `js/render-helpers.js`. SVG path definitions from `res/drawable/ic_*.xml` are in `js/icons.js`.

Translations: Android `res/values/strings.xml`, `res/values-en/strings.xml`, `res/values-ru/strings.xml`, and theme label resources; web `locales/{he,en,ru}.json` and `js/i18n.js`. Navigation and temporary UI state are in `js/state.js` and event handling in `js/app.js`. All web state resets on reload.

Fidelity limits: Android system bars, IME behavior, platform font metrics, native ripple effects, and precise screenshot comparison are unavailable in the web reconstruction. Android uses resource drawables; web icons retain their vector paths but are rendered by SVG.
