# ManyManager UI reference

An interactive HTML/CSS/JavaScript companion to the Android XML interface. Both implementations share navigation and category/source interactions. Android uses persistent Room data; the browser uses an isolated in-memory preview.

## Run

From the repository root:

```sh
python -m http.server 8000 --directory ui-reference
```

Open http://localhost:8000/ . The entry point is `index.html`. ES modules and fetched locale JSON require an HTTP server.

## Screens and interactions

The side menu opens Home, Expense Summary, Budget Management, Expense Categories, and Settings. Settings contains only Language and Appearance. Expense Categories retains its existing list, Back action to the previous page, and Add Category dialog.

Budget Management keeps the existing month arrows and total card. It lists the same source identities offered in the expense sheet. Add Source accepts a name and an initial allocation for the selected budget period, captured when the form opens. Tapping a source opens its editor with a source name, current allocation, positive adjustment magnitude, +/− selector, signed resulting allocation preview, Cancel, and Update budget. Empty adjustment means zero change. A confirmed adjustment changes the selected period’s allocation once and never creates an expense. The existing allocation rule rejects a negative allocated budget; its signed preview remains visible. Remaining balance may be negative.

The Add Expense sheet offers “＋ New category” after its category choices, including when the list is empty. It reuses the Add Category form. Category choices and draft selection use stable category IDs. Confirming selects the new category by ID; Cancel and Back/Escape return to the unchanged expense draft. Amount, source, date, note, and category selection remain in the draft during this transition. Android keeps the expense sheet beneath the category dialog; the web reference temporarily renders the category dialog and restores the same draft.

The initial state is Hebrew, RTL, light theme, home screen, current month, and no categories, sources, budgets, or expenses. Android categories, budget sources, and period allocations persist in Room SQLite across process restarts, whereas browser preview categories, budget sources, allocations, language, theme, and drafts live in memory in the browser preview. The Android expense draft is restored across configuration changes while its sheet is open. Expense saving remains unavailable in both implementations; no sample data or storage service is present.

## Budget periods and verification

Periods run from the 10th through the 9th by default. Android uses the saved calculator setting through BudgetPeriodCalculator; the browser has one civil-date helper in js/budget-period.js. Budget Management enters the active period and its arrows select adjacent periods. Forms retain their captured period. Home follows the active period independently of management selection, including before the 10th. The preview refreshes on return/focus and checks visible civil-date changes once a minute, while captured form periods and historical selections remain unchanged. A new period has zero allocation and retains all sources, with no copying or carry-over. Summary arrows remain disabled.

Android observes sources independently from financial snapshots and renders loading, empty, populated, and error states with explicit retry. Source form submission is owned by the ViewModel; pending forms cannot submit twice or be cancelled. Failed writes retain the input. Resume and date/time/zone broadcasts refresh active data without replacing historical selections. The preview is synchronous, resets on reload, and has no simulated production storage or automatic retries. Its money range is JavaScript safe integers in minor units, narrower than Android Long.

Serve this directory and open /tests/stage6.html to run 19 focused browser assertions. They cover period boundaries, stable source IDs, atomic validation, zero allocations, draft preservation and category return/cancel. These checks passed in Chrome on Android 17. They do not prove pixel parity or Android lifecycle behavior. Android verification and remaining lifecycle checks are recorded in docs/persistent-data-plan.md.

## Viewport and fidelity

The layout is capped at 440 CSS px on desktop and fills a narrow phone browser. The Android source uses a 440dp cap with system insets. CSS px are not physical screenshot pixels. Browser font metrics, native EditText behavior, IME, system bars, ripple effects, and native AlertDialog appearance can differ. No fake system bars are drawn.

This document describes the intended behavior. Check the current task report for verification actually performed. Browser and Android rendering can differ; pixel-level visual equivalence has not been established.
