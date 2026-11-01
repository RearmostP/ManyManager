# ManyManager UI reference

An interactive HTML/CSS/JavaScript companion to the Android XML interface. Both implementations share navigation, category/source interactions, expense creation, and amount correction. Android uses persistent Room data; the browser uses an isolated in-memory preview.

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

The initial state is Hebrew, RTL, light theme, home screen, current month, and no categories, sources, budgets, or expenses. Android categories, budget sources, period allocations, and expenses persist in Room SQLite across process restarts. Browser categories, sources, allocations, expenses, appearance, and drafts live only in memory and reset on reload. No sample records or browser storage service are present.

## Expenses

Save accepts an exact positive amount in minor units, an optional category (Uncategorized), a required persistent source, a civil date, and an optional note. Zero and negative amounts/refunds are unsupported. Zero-allocation sources and negative remaining balances are allowed. Android uses a native date picker and LocalDate; the preview uses a date input and YYYY-MM-DD civil strings through the central period helper. The expense date determines its accounting period.

All expenses shows the active period, newest date first with an ID tie-breaker. Tap a row to correct only its amount. Cancel leaves it unchanged; confirmation updates the same record. Home totals, counts and remaining balances derive from the same snapshot. Allocations stay unchanged. Category and source tabs now show the active period’s spending and expense count, ordered by spending descending then stable ID (null category first on a tie). Duplicate names remain separate rows. Uncategorized is a translated null group, not a category record. Only groups with expenses appear, including zero-allocation sources. Breakdown rows have no actions. Deletion and editing other expense fields remain outside scope.

Android ViewModel owns editable/submitting/success/failure states for both expense forms. Pending writes cannot be cancelled or submitted twice; successful commit closes once. Known failures preserve input for explicit retry. Normal Activity recreation and configuration rebuild restore the form without replay. A restored Bundle marked submitting locks the form: close and inspect All expenses before deliberately starting another operation. A Bundle is not durable draft storage or an exactly-once guarantee after arbitrary process death, including a stale Bundle saved before submission.

## Summary state and consistency

Android SummaryBreakdownUi renders both panels from the same activeBudget snapshot as Home and All expenses. Category names and grouped amounts come from that snapshot. Independent category/source observations still serve choices and management; their loading or failure cannot replace a valid financial panel. Financial loading, successful empty, and failure with retry are distinct.

SourceBudget already contains spending/count/remaining. BudgetCalculations orders category groups, and BudgetSnapshot exposes ordered sourceBreakdown without regrouping expenses. Browser snapshotFor prepares the same rows in the isolated preview model; screens only format and bind them. No percentages or charts were added. Home and all summary tabs use one callback/revision. Budget Management and choice streams update independently and converge on committed facts; no claim of simultaneous frames across streams is made.

Stage 9 checks in /tests/stage9.html passed all 35 assertions in Chrome on Android 17, including real app save/correction events. Android passed 32 JVM, 37 repository/controller, 12 database, and 12 Activity/dialog checks after correcting two test method signatures. Long-name rows passed geometry/semantic checks at 320dp in he/ru/en and light/dark; manual TalkBack, enlarged fonts and API 30 remain unverified. Stage 10 still covers focused cleanup and final acceptance; the project is not declared complete.

## Budget periods and verification

Periods run from the 10th through the 9th by default. Android uses the saved calculator setting through BudgetPeriodCalculator; the browser has one civil-date helper in js/budget-period.js. A deliberate menu entry into Budget Management selects the active period and its arrows select adjacent periods. Back restores the historical selection; rebuilding Android views or recreating the Activity keeps it. Forms retain their captured period. Home follows the active period independently of management selection, including before the 10th. The preview refreshes on return/focus and checks visible civil-date changes once a minute, while captured form periods and historical selections remain unchanged. A new period has zero allocation and retains all sources, with no copying or carry-over. Summary arrows remain disabled.

Android observes sources independently from financial snapshots and renders loading, empty, populated, and error states with explicit retry. Source form submission is owned by the ViewModel; pending forms cannot submit twice or be cancelled. Known failed writes retain the input. On Android, a Bundle restored from an interrupted source write has an unknown outcome: that form is locked against resubmission. Close and check saved data returns to its captured period; a new operation must be started deliberately. Bundle recovery is not a durable draft or an exactly-once guarantee after arbitrary process termination. The browser does not emulate Android process lifecycle. Resume and date/time/zone broadcasts refresh active data without replacing historical selections. The preview is synchronous, resets on reload, and has no simulated production storage or automatic retries. Its money range is JavaScript safe integers in minor units, narrower than Android Long.

Serve this directory and open /tests/stage8.html for 32 expense model, component and real app event checks; all passed in Chrome on Android 17. Android verification passed 31 JVM checks, 36 repository/controller checks, 12 database checks, and 9 real Activity/dialog checks after fixing an existing rollover test wait. The full language/theme visual matrix and API 30 were not reverified for the new expense dialogs. Open /tests/stage6.html to run 27 focused browser assertions (stages 6–7). They cover period boundaries, stable source IDs, atomic validation, zero allocations, draft preservation and category return/cancel. The checks include year-crossing labels with both years, accessible localized date ranges, and confirmation/cancellation after period rollover. They passed in Chrome on Android 17. They do not prove pixel parity or Android lifecycle behavior. Android verification and remaining lifecycle checks are recorded in docs/persistent-data-plan.md.

## Viewport and fidelity

The layout is capped at 440 CSS px on desktop and fills a narrow phone browser. The Android source uses a 440dp cap with system insets. CSS px are not physical screenshot pixels. Browser font metrics, native EditText behavior, IME, system bars, ripple effects, and native AlertDialog appearance can differ. No fake system bars are drawn.

This document describes the intended behavior. Check the current task report for verification actually performed. Browser and Android rendering can differ; pixel-level visual equivalence has not been established.
