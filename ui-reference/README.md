# ManyManager UI reference

An interactive HTML/CSS/JavaScript companion to the Android XML interface. Both implementations share the same navigation and in-memory category and budget-source flows.

## Run

From the repository root:

```sh
python -m http.server 8000 --directory ui-reference
```

Open http://localhost:8000/ . The entry point is `index.html`. ES modules and fetched locale JSON require an HTTP server.

## Screens and interactions

The side menu opens Home, Expense Summary, Budget Management, Expense Categories, and Settings. Settings contains only Language and Appearance. Expense Categories retains its existing list, Back action to the previous page, and Add Category dialog.

Budget Management keeps the existing month arrows and total card. It lists the same source identities offered in the expense sheet. Add Source accepts a name and an initial allocation for the selected month. Tapping a source opens its editor with a source name, current allocation, positive adjustment magnitude, +/− selector, signed resulting allocation preview, Cancel, and Update budget. Empty adjustment means zero change. A confirmed adjustment changes the selected month's allocation once and never creates an expense. The existing allocation rule rejects a negative allocated budget; its signed preview remains visible. Remaining balance may be negative.

The Add Expense sheet offers “＋ New category” after its category choices, including when the list is empty. It reuses the Add Category form. Confirming selects the new category; Cancel and Back/Escape return to the unchanged expense draft. Amount, source, date, note, and category selection remain in the draft during this transition. Android keeps the expense sheet beneath the category dialog; the web reference temporarily renders the category dialog and restores the same draft.

The initial state is Hebrew, RTL, light theme, home screen, current month, and no categories, sources, budgets, or expenses. Category and source data, allocations, language, theme, and drafts live only in JavaScript memory and reset on reload or tab reopening. Android category/source data lives in its Activity ViewModel, surviving ordinary configuration changes but not process death. The Android expense draft is also restored across configuration changes while its sheet is open. Expense saving remains unavailable in both implementations; no sample data or storage service is present.

## Viewport and fidelity

The layout is capped at 440 CSS px on desktop and fills a narrow phone browser. The Android source uses a 440dp cap with system insets. CSS px are not physical screenshot pixels. Browser font metrics, native EditText behavior, IME, system bars, ripple effects, and native AlertDialog appearance can differ. No fake system bars are drawn.

This document describes the intended behavior. Check the current task report for verification actually performed. Browser and Android rendering can differ; pixel-level visual equivalence has not been established.
