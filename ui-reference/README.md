# ManyManager visual playground

A lightweight visual baseline for trying designs before implementing approved changes in Android. Keep screens, typography, spacing, colors, icons, rounded cards, themes, languages and essential interaction states aligned with the current Android UI. [Current application documentation](../docs/README.md) describes production behavior.

Run from the repository root:

```sh
python preview.py
```

The [launcher](../preview.py) serves only this directory on 127.0.0.1, selects an available port and opens your default browser. It also works by absolute script path from another directory. The terminal prints the URL; stop with Ctrl+C.

The default [fixture](js/fixture.js) has exactly **3 categories, 7 distinct expenses total and 2 sources**, with fictional names, varied dates/amounts and short/long/absent notes. The fixed demonstration date is 25 November 2026, inside the 10 November–9 December period; real time never hides the examples. Edit this one file to change examples. All changes, language/theme choices and drafts reset on reload.

- [Screens](js/screens/) and [dialog components](js/components/) retain the existing markup.
- [Tokens](css/tokens.css), [base](css/base.css), [components](css/components.css) and [screen styles](css/screens/) define appearance; [icons](js/icons.js) and [translations](locales/) are reused.
- [app.js](js/app.js) handles navigation, dialogs, tabs, expansion, edit/save/cancel/delete, language/theme and budget +/− demonstrations.
- [state.js](js/state.js) holds only temporary interaction state; [demo.js](js/demo.js) supplies small display sums/filtering, with [dates.js](js/dates.js) for labels and month arrows.

Unified Summary cards expand rows in place. All Expenses is ungrouped. Details open over Summary, read-only first, editing in the same panel. Closing returns to the tab/expansion/scroll position, clamped if content shrinks. Category/source creation and expense/budget changes are disposable demonstrations, not production operations.

There is no persistence, repository, observer pipeline, transaction/overflow engine, storage failure simulation or Android lifecycle guarantee. Basic input checks only keep the demo usable; they are not Android validation.

When changing Android UI, update the affected preview screen and essential interaction states. Keep the implementation lightweight; do not reproduce production business logic or lifecycle guarantees. Verify the affected screen/interaction with a focused browser smoke/visual check, not comprehensive browser business-logic regression suites for every Android change. Browser fonts, controls, IME and system bars can differ; pixel equivalence is not established. See [UI_MAP.md](UI_MAP.md).
