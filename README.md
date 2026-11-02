# ManyManager

Android budget and expense manager with the existing XML/Views interface, Hebrew, Russian and English, RTL/LTR, and light/dark themes. The Gradle project contains `:androidApp` and `:shared`; shared retains the Compose sample and is not the production persistence layer.

The agreed local persistence scope is implemented: categories, budget sources, allocations by period, positive expenses with optional categories and required sources, amount-only correction, and derived totals, balances and breakdowns. Categories and sources have stable identities; renaming preserves references. Zero allocation and negative remaining balance are allowed. Periods default to the 10th–9th, with no copying or carry-over. Home and Summary follow the active period independently of historical Budget Management selection.

Android uses Room schema 1 in app-private `databases/many-manager.db`. Business operations go through `ManyManagerRepository`, observed through `PersistentDataViewModel`. No sample business data is seeded. Settings remain outside Room. Core operations require no network; the merged debug manifest has no INTERNET permission.

The stage-10 instrumentation rerun passed all 61 tests on SM-S928B / Android 17 (37 repository/controller, 12 database, 12 Activity/dialog), including the strengthened schema-1 preservation test. Both APKs remained installed. Earlier connection/install failures remain documented. Release preparation added and passed the continuous file-reopen-to-fresh-UI acceptance scenario; the full device suite now passes 62 tests. Other verification limits remain explicit. See the [acceptance report](docs/persistent-data-acceptance.md) and [ten-stage plan/results](docs/persistent-data-plan.md).

See [the private signed release guide](docs/release.md) for building and installing version 0.1.0, local signing and key backup.

## Build and checks

Use Android Studio or a compatible JDK (this work used Android Studio JBR as JAVA_HOME):

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :androidApp:lintDebug
.\gradlew.bat :androidApp:assembleDebugAndroidTest
.\gradlew.bat :androidApp:connectedDebugAndroidTest
```

Before instrumentation, verify `android.injected.androidTest.leaveApksInstalledAfterRun=true`, the isolated test Application/runner, and the installed package state. Do not clear data or uninstall. Inspect the device test count and runner output as well as Gradle's exit code; a disconnected-device install failure can produce a green build with zero tests.

## Storage limits

The project does not encrypt its data. App-private storage is neither encryption nor a guaranteed backup. `allowBackup=true`, with no explicit database exclusions, means Android backup/device transfer may include the database. Product decision for 0.1.0: keep automatic Android backup and device-transfer eligibility, without excluding the database. Actual backup/restore remains untested.

There is no expense deletion, refund/nonpositive expense, editing of other expense fields, historical Summary navigation, multiple-calculator UI, account, cloud sync, encryption or export. Bundle restoration is not durable draft recovery or exactly-once writing across arbitrary process death.

The [HTML companion](ui-reference/README.md) mirrors the feature in isolated JS memory and resets on reload. It does not persist browser data or replace Android storage.
