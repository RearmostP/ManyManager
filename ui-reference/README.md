# ManyManager UI reference

An interactive HTML/CSS/JavaScript companion to the Android XML interface. Both implementations now show the simplified Settings, category management, and source and budget management.

## Run

From the repository root:

```sh
python -m http.server 8000 --directory ui-reference
```

Open http://localhost:8000/ . The entry point is `index.html`. ES modules and fetched locale JSON require an HTTP server.

## Screens and interactions

Home, expense summary, Settings, Budget Management, and the dedicated Expense Categories screen are available through the existing side menu or screen links. Settings contains Expense Categories, Language, and Appearance. Expense Categories opens a separate page with back navigation and an Add Category dialog.

Budget Management keeps the existing month arrows and total card. It lists the same source identities offered in the expense sheet. Add Source accepts a name and an initial allocation for the selected month. Tapping a source opens its editor with a source name, current allocation, positive adjustment magnitude, +/− selector, signed resulting allocation preview, Cancel, and Update budget. Empty adjustment means zero change. A confirmed adjustment changes the selected month's allocation once and never creates an expense. The existing allocation rule rejects a negative allocated budget; its signed preview remains visible. Remaining balance may be negative.

The initial state is Hebrew, RTL, light theme, home screen, current month, and no categories, sources, budgets, or expenses. Category and source data, allocations, language, theme, and drafts live only in JavaScript memory and reset on reload or tab reopening. Android's new category/source operations similarly live in its Activity ViewModel, surviving ordinary configuration changes but not process death. Expense saving remains unavailable in both implementations; no sample data or storage service is present.

## Viewport and fidelity

The layout is capped at 440 CSS px on desktop and fills a narrow phone browser. The Android source uses a 440dp cap with system insets. CSS px are not physical screenshot pixels. Browser font metrics, native EditText behavior, IME, system bars, ripple effects, and native AlertDialog appearance can differ. No fake system bars are drawn.

The Android `:androidApp:assembleDebug` build and `:androidApp:testDebugUnitTest` passed (14 unit tests). The debug app was launched on a connected Samsung device. Settings, category navigation and dialog, Budget Management, source creation, −₪100 preview and cancellation, confirmation, and month isolation were checked on device. The web reference was loaded through the local HTTP server and exercised in the browser: Settings removal; category navigation, cancel and add; source add, edit, cancel, month isolation, +/− preview, negative allocation validation, home budget refresh, expense source selection, English and Russian LTR, Hebrew RTL, and dark theme. No browser console errors appeared during those checks. Android and browser budget screens were inspected at their respective 440dp and 440 CSS px caps; pixel-level visual equivalence has not been established.
