# ManyManager Simple UI reference

Current behavior: [branch documentation](../docs/README.md). This preview follows `original`; `main` is the personal edition.

This is a lightweight, in-memory design playground for the native Android XML/Views app. Reload resets all examples, drafts and appearance choices. It does not emulate Room, migrations or Android lifecycle guarantees.

## Run and examples

From the repository root, serve `ui-reference` over HTTP:

```sh
python -m http.server 8000 --directory ui-reference
```

Open http://localhost:8000/. This branch has no `preview.py` launcher. ES modules and locale JSON require HTTP.

The default starts empty. Fictional examples are in `js/fixture.js`; import it explicitly for visual experiments. It demonstrates ₪20,000 budget, ₪100 cash and ₪250 card expenses, leaving ₪19,650. The optional `tests/summary-cards.html` fixture demonstrates long names, notes and expanded groups.

## Budget and payment methods

Budget Management retains period arrows, the date range and total card, and offers Update budget for the selected period. A separate Payment methods section has Add and Rename actions. Method forms accept a name alone; no initial allocation, adjustment controls or method balance exists. A budget form captures its selected period.

Each period has one budget in integer minor units. Remaining is that budget minus the expenses dated in the period. The 10th starts a new period; the 9th ends the previous one. New periods have zero budgets and keep methods. Spending with zero budget or negative remaining is allowed.

Home shows active-period spending and remaining; its expanded panel shows total budget and spending by payment method. Summary keeps category and payment-method spending/counts with expandable expense rows in unified rounded cards, plus All Expenses. Methods have no budgets or remaining balances.

Each expense requires a payment method and a positive amount, with optional category/note and a civil date. Category creation preserves the expense draft. Details open read-only; Edit changes the same ID in place, Cancel discards the draft, Delete requires confirmation. Edits and deletion immediately recalculate totals, including moves across period boundaries. Renaming a method preserves expense references and spending.

Hebrew RTL, Russian/English LTR and light/dark appearances remain supported. Native dialogs, fonts, IME and system bars can differ from HTML; pixel parity is not asserted.

## Optional subcategories (HTML prototype only)

One category field opens a two-step picker. A main category without children selects immediately; a parent opens General plus its subcategories. General assigns directly to the parent. Back returns to main categories and Cancel preserves all draft fields. Details and expense rows show the selected path.

Category management opens a parent’s children and offers Add subcategory. Stable category IDs and optional parent IDs allow exactly two levels. Summary cards combine parent and child expenses once, with nonempty General/child sections when expanded. The fixture reuses the ₪100 and ₪250 records for direct Food and Supermarket expenses; Health has no children. Budget and payment methods are unchanged.

This is an initial, experimental design for testing and feedback, not a finalized interface. The flow currently exists only in the HTML prototype and awaits Android implementation.

## Checks

Serve the preview and open:

- `tests/subcategories.html`: two-level picker/management, General, draft preservation, stable IDs, exact totals, editing/deletion and 320px locale/theme layouts.
- `tests/single-budget-ui.html`: real budget entry, name-only method forms, remaining balance and summaries.
- `tests/stage6.html`: single budget example, zero budget, required method, rename, edit/delete and 9th/10th boundary moves.
- `tests/stage8.html`: expense/category forms and real app events.
- `tests/stage9.html`: spending breakdown consistency and real app events.
- `tests/expense-details.html`: read-only/edit/delete flows and narrow locale/theme layouts.
- `tests/summary-cards-checks.html`: unified card/expansion/detail interactions.

For a narrow visual review, `tests/subcategories.html?visual=picker&lang=he&theme=light` leaves the picker open after the checks; `lang=ru` / `en` and `theme=dark` are also supported. The long mixed-language name intentionally stress-tests wrapping.

Subcategory verification: Chrome headless passed all seven check pages (228 assertions), including 34 new subcategory assertions and 320px Hebrew/Russian/English light/dark layout checks. Real Samsung-device usability remains unverified.

Checks use only isolated memory. Historical result counts are not fresh verification; see the current task report for actual outcomes. Android additionally tests transactions, populated version-1 migration and overflow rollback. Keep this preview focused on visual behavior rather than reproducing production persistence or lifecycle.
