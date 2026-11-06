# Presentation port from original to main

## Starting state (2026-11-06)

Reviewed branch-local AGENTS.md, current docs/README.md, UI reference documentation, release guidance and actual branch diffs before editing. No archived documents were changed.

- main and origin/main: `f89b54a84fd1e62ad4101b92985921db8762935d`.
- original and origin/original: `8b68a393c0102fd4d2878fa7a68bf45a406c7ae0`.
- The only initial working-tree changes were the previously requested launcher art, foreground inset/background and ten density icons, plus `scripts/generate-launcher-icons.ps1`. Those exact working-tree assets are the baseline being preserved and included in this delivery.
- main applicationId, namespace and Kotlin packages: `com.example.manymanager`; Application: `.ManyManagerApplication`; launcher Activity: `.MainActivity`.
- versionCode: **2**; versionName: **0.2.0**. Signing/build configuration is unchanged.
- Manifest launcher: `@mipmap/ic_launcher`, round launcher: `@mipmap/ic_launcher_round`. Adaptive icons use `@drawable/ic_launcher_background` (white) and `@drawable/ic_launcher_foreground` (bitmap `manymanager_launcher_art`, 18% inset).

## Existing differences that must remain

| Area | main (preserved) | original (not ported) |
| --- | --- | --- |
| Budget | Per-source period allocations; total is their sum; source create/edit includes initial allocation and +/− adjustment | A separately stored single period budget; name-only payment methods |
| Categories | Flat and optional; null/Uncategorized can be selected, created from an expense draft | Required main/child selection; two-level hierarchy; creation only in management |
| Room | Schema 1, budget_allocations, existing keys/relationships and validation | Schema 3, period_budgets, 1→2 and 2→3 migrations, category parent keys/triggers |
| Financial rules | Existing exact Long amounts, BigInteger aggregation, overflow rollback, period boundaries, source balances | Different budget ownership and category requirements |

These existing data-model differences necessarily keep different budget controls, category navigation and corresponding translations. Visual/interaction parity does not authorize a total-budget editor, name-only source writes, mandatory categories, hierarchy, synthetic children or a data migration on main.

## Selective presentation transfer

- Ported original's inline selected-category checkmark, full-width wrapping choices and spacing, retaining main's flat categories, Uncategorized and creation shortcuts.
- Adapted original's edit-category picker to main's optional flat categories. Opening and cancelling only browses; tapping a row changes the draft, not the saved expense.
- Ported original's budget-dialog surface, primary/cancel styling, width and dimming to main's source allocation dialogs, preserving their period capture, fields, preview and submit callbacks.
- Home's expanded breakdown now shows source spending from the existing snapshot, like original. Total allocated and remaining calculations are unchanged; accessibility wording describes spending.
- Mirrored these controls in the lightweight HTML playground. Retained main's fictional fixture, allocation demonstrations, dates/demo helpers and Python launcher; original's observer/validation preview model was not imported.
- Preserved the existing shared shell, Summary cards, row formatting, details/edit/delete flow, languages and themes. Their branch differences were reviewed: remaining changes there are package identity, category hierarchy or budget-model wiring.
- Corrected active release documentation's stale claim that the branches still shared an applicationId.

## Preserved-file baseline

Git blob hashes below were taken from the working tree before the port (including the uncommitted icon). Final verification compares to these values, rather than reverting to main's old committed icon.

```text
00686566992c0896d38317842246ff7527c31d97 androidApp/build.gradle.kts
5229cbc7557a7bb09a5fbe937a0c4e25d23e4983 androidApp/src/main/AndroidManifest.xml
f635da860175541754035015f6a54d76c140d3d2 androidApp/src/main/res/drawable-nodpi/manymanager_launcher_art.png
7fc59d1f37dd841ebc8b461a86b672f9a7d17269 androidApp/src/main/res/drawable-v24/ic_launcher_foreground.xml
faeb7b26326848b61711737835e46895e8513063 androidApp/src/main/res/drawable/ic_launcher_background.xml
eca70cfe52eac1ba66ba280a68ca7be8fcf88a16 androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
eca70cfe52eac1ba66ba280a68ca7be8fcf88a16 androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
5dcc0f3e561746fc017998012cd1da05cb322634 androidApp/src/main/res/mipmap-hdpi/ic_launcher.png
5dcc0f3e561746fc017998012cd1da05cb322634 androidApp/src/main/res/mipmap-hdpi/ic_launcher_round.png
48f28ba923429551eb2a8ba57e31a8313c2c5f86 androidApp/src/main/res/mipmap-mdpi/ic_launcher.png
48f28ba923429551eb2a8ba57e31a8313c2c5f86 androidApp/src/main/res/mipmap-mdpi/ic_launcher_round.png
311f5c15d5c5a0cef2f01d2b99925283818dc486 androidApp/src/main/res/mipmap-xhdpi/ic_launcher.png
311f5c15d5c5a0cef2f01d2b99925283818dc486 androidApp/src/main/res/mipmap-xhdpi/ic_launcher_round.png
d4d5e95f291355999749b1215f9f8f06950b66aa androidApp/src/main/res/mipmap-xxhdpi/ic_launcher.png
d4d5e95f291355999749b1215f9f8f06950b66aa androidApp/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
90c5b8a97742c6b5415cd9986ea28abf37968d07 androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png
90c5b8a97742c6b5415cd9986ea28abf37968d07 androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
e6088b4d294b37566783410d5f8e2d0bb39be016 preview.py
```

## Verification

Checks actually run on 2026-11-06:

- Android Studio Gradle: `:androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:lintDebug :androidApp:assembleDebugAndroidTest` succeeded. JVM result: **32 passed**, no failures/skips. The normal build was run again after isolated instrumentation to restore normal applicationId artifacts.
- Samsung SM-S928B, Android 17: **72 instrumentation tests passed** in the final full run, including the new real picker/draft/null-category test and existing repository, file-reopen, database, validation, overflow, allocation and lifecycle tests. Command: `:androidApp:connectedDebugAndroidTest --init-script build/presentation-port-tests.init.gradle --no-configuration-cache`.
- That local, ignored init script uses `androidComponents.finalizeDsl` to override only the test run's applicationId to `com.example.manymanager.presentationtest` and testApplicationId to `com.example.manymanager.presentationtest.test`. Namespace/classes remain main's. Confirmed the isolated test Application, in-memory/UUID databases, prefixed test preferences and `leaveApksInstalledAfterRun=true` before running. Existing production packages were not cleared, removed or replaced; isolated APKs remain installed.
- Earlier instrumentation runs exposed one obsolete home-remaining assertion (updated to spending without changing budget/remaining assertions), then a recreation readiness race in an existing fault-injection test. Added a readiness wait/assertion and finally cleanup of that test's failure trigger; this prevents cascading fixture failures. Final full result above is clean; no production logic was changed to satisfy tests.
- `python preview.py` launched successfully at 127.0.0.1:8000. Chrome headless ran `tests/presentation-port.html`: **160 assertions passed**, driving actual app events for picker cancel/confirmation, same-name IDs, optional categories, unchanged allocations, edit/save, delete cancel, creation draft preservation and 320px layouts across Hebrew/English/Russian and light/dark.
- Visually inspected generated screenshots of the Hebrew light picker, Russian dark source-budget dialog and English light creation form at 320px preview width. This verifies those sampled layouts, not pixel equivalence between Android and HTML.
- All **18 recorded file hashes** match the pre-port working-tree baseline exactly, including all icon files, build configuration, manifest and Python launcher.
- `aapt dump badging` on the final normal debug APK confirms `com.example.manymanager`, versionCode `2`, versionName `0.2.0`, launcher `com.example.manymanager.MainActivity` and `ic_launcher.xml`.
- Final source diff contains no changes to data/domain packages, Room schemas/migrations, PersistentDataViewModel, BudgetAmounts, MainActivity, ManyManagerApplication, signing/build configuration or archived documentation. `git diff --check` passed.
- Both local original and remote refs/heads/original remain `8b68a393c0102fd4d2878fa7a68bf45a406c7ae0`.

Not run: release/signing/distribution checks, API 30 runtime, backup/device-transfer restoration, manual TalkBack/large-font/physical-keyboard audit, exhaustive Android/HTML pixel comparison or arbitrary process death during commit. No release, tag or APK upload was performed.
