# Visual UI map

[Android behavior](../docs/README.md) remains authoritative. Run `python preview.py` from the repository root; [fixture.js](js/fixture.js) is the single default example set, reset on reload.

| Visual area | Android reference (under androidApp/src/main/) | Playground |
| --- | --- | --- |
| Shell, menu, navigation | MainActivity.kt, activity_main.xml, SideMenuDialog.kt | index.html, js/app.js, js/state.js, js/components/drawer.js |
| Home and budget breakdown | BudgetHomeUi.kt, page_home.xml | js/screens/home.js, css/screens/home.css |
| Summary cards and rows | SummaryBreakdownUi.kt, AllExpensesUi.kt, row_summary_breakdown.xml, row_summary_expense.xml | js/screens/summary.js, css/screens/summary.css |
| Expense creation and selected category rows | ExpenseSheetDialog.kt, dialog_expense.xml, ic_check.xml | js/components/expense-sheet.js |
| Read-only details, in-place edit, category picker and confirmation | ExpenseDetailsDialog.kt, CategoryPickerDialog.kt | js/components/expense-details.js, category-picker.js, expense-delete.js, css/components.css |
| Budget list and +/− preview | BudgetManagementUi.kt, BudgetSourceDialogs.kt, BudgetDialogWindow.kt | js/screens/budget.js, js/components/source-dialog.js |
| Categories | CategoryManagementUi.kt, CategoryCreationDialog.kt | js/screens/categories.js, js/components/category-dialog.js |
| Languages and themes | MainActivity.kt, res/values*/strings.xml, colors.xml, styles.xml | js/screens/settings.js, js/i18n.js, locales/, css/tokens.css |

Home's expanded breakdown displays source spending. Category creation keeps inline selected rows; editing uses a separate picker that changes only the draft on confirmation and preserves it on Cancel/Back. main retains flat/optional categories, Uncategorized, category-creation shortcuts and source allocation controls; original's hierarchy and single-budget storage are not applicable here. A focused real-event check lives in tests/presentation-port.html.

The preview reuses the established styles, icons and translations. Shared spacing/controls live in css/base.css and css/components.css; individual layouts live in css/screens/. js/render-helpers.js formats the display; js/demo.js only sums/filters examples so visible totals agree. js/dates.js formats the fixed period and navigable budget labels.

Keep unified rounded group cards, separately clickable headers/expense rows, count/total/chevron, contextual secondary text and two-line notes. Lists use normal page scroll. Details overlay Summary; read/edit share a panel, with Save/Cancel at start and Delete at end in RTL/LTR. Preserve tab, expanded IDs and scroll through ordinary interactions. Do not add intermediate group screens or use the Add Expense sheet as the details editor.

Navigation, dialogs, edits and budget changes are in-memory visual demonstrations. Android storage, financial validation, transactions, asynchronous failures and process recovery are intentionally not reproduced. Keep Android and HTML visually aligned, including essential interaction states, while keeping HTML lightweight. Verify only the affected screen/interaction when it changes; comprehensive browser business-logic suites are not required for every Android change. Native font metrics and pixel parity are unverified.
