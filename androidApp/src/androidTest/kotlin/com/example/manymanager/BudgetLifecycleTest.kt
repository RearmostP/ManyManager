package com.example.manymanager

import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.manymanager.domain.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real MainActivity/dialog integration. Only this runner's in-memory database is fault-injected. */
@RunWith(AndroidJUnit4::class)
class BudgetLifecycleTest {
    private val app get() = ApplicationProvider.getApplicationContext<BudgetTestApplication>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun prepare() = runBlocking {
        app.databaseInitialization.await().getOrThrow()
        withContext(Dispatchers.IO) { app.database.clearAllTables() }
        app.database.dao().initializeLocalCalculator()
        app.clock.now = Instant.parse("2026-11-09T21:59:00Z")
        app.clock.currentZone = ZoneId.of("Asia/Jerusalem")
    }

    @After fun finish() {
        scenario?.close()
        scope.cancel()
    }

    private fun launch(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    private fun model(screen: ActivityScenario<MainActivity>): PersistentDataViewModel {
        lateinit var result: PersistentDataViewModel
        screen.onActivity { result = ViewModelProvider(it)[PersistentDataViewModel::class.java] }
        return result
    }

    private fun awaitUi(screen: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            screen.onActivity { done = condition(it) }
            if (done) return
            SystemClock.sleep(25)
        }
        fail("Timed out waiting for Activity/dialog state")
    }

    private fun rootWith(id: Int): View? = WindowInspector.getGlobalWindowViews()
        .lastOrNull { it.findViewById<View>(id)?.isShown == true }

    private fun openBudget(screen: ActivityScenario<MainActivity>) {
        screen.onActivity { it.findViewById<View>(R.id.menuButton).performClick() }
        awaitUi(screen) { rootWith(R.id.nav3) != null }
        screen.onActivity { rootWith(R.id.nav3)!!.findViewById<View>(R.id.nav3).performClick() }
        awaitUi(screen) {
            rootWith(R.id.nav3) == null && it.findViewById<View>(R.id.budgetPage).isShown &&
                it.findViewById<View>(R.id.budgetAddSource).isEnabled
        }
    }

    private fun addForm(screen: ActivityScenario<MainActivity>, name: String, amount: String) {
        screen.onActivity { it.findViewById<View>(R.id.budgetAddSource).performClick() }
        awaitUi(screen) { rootWith(R.id.addSourceName) != null }
        screen.onActivity {
            val root = rootWith(R.id.addSourceName)!!
            root.findViewById<EditText>(R.id.addSourceName).setText(name)
            root.findViewById<EditText>(R.id.addSourceAmount).setText(amount)
        }
    }

    private suspend fun gate(): Pair<CompletableDeferred<Unit>, Job> {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = scope.launch {
            app.database.withTransaction { entered.complete(Unit); release.await() }
        }
        withTimeout(10_000) { entered.await() }
        return release to job
    }

    @Test fun creationSurvivesRecreateAndConfigurationRebuildWithoutSecondWrite() = runBlocking {
        val screen = launch()
        openBudget(screen)
        val controller = model(screen)
        val historical = (controller.state.value as PersistentDataState.Ready).snapshot.period
        screen.onActivity { controller.selectPreviousPeriod() }
        awaitUi(screen) { controller.selectedPeriod != null &&
            (controller.state.value as? PersistentDataState.Ready)?.snapshot?.period == controller.selectedPeriod }
        val selected = controller.selectedPeriod!!
        assertNotEquals(historical, selected)
        addForm(screen, "Saved form", "12.34")
        val (release, lock) = gate()
        try {
            screen.onActivity {
                rootWith(R.id.addSourceName)!!.findViewById<View>(android.R.id.button1).performClick()
                assertTrue(controller.sourceCreationState.value!!.submitting)
            }
            screen.recreate()
            assertSame(controller, model(screen))
            awaitUi(screen) { rootWith(R.id.addSourceName) != null }
            screen.onActivity {
                val root = rootWith(R.id.addSourceName)!!
                assertEquals("Saved form", root.findViewById<EditText>(R.id.addSourceName).text.toString())
                assertEquals("12.34", root.findViewById<EditText>(R.id.addSourceAmount).text.toString())
                assertFalse(root.findViewById<View>(android.R.id.button1).isEnabled)
                assertFalse(root.findViewById<View>(android.R.id.button2).isEnabled)
                assertEquals(selected, controller.sourceCreationState.value!!.period)
                assertEquals(selected, controller.selectedPeriod)
                // Exercise the app's existing view-rebuild path without changing device settings.
                val config = Configuration(it.resources.configuration)
                config.uiMode = config.uiMode xor Configuration.UI_MODE_NIGHT_MASK
                it.onConfigurationChanged(config)
            }
            awaitUi(screen) { rootWith(R.id.addSourceName) != null }
        } finally { release.complete(Unit); lock.join() }
        awaitUi(screen) { controller.sourceCreationState.value == null && rootWith(R.id.addSourceName) == null }
        val saved = success(app.repository.snapshot(selected))
        assertEquals(1, saved.sources.size)
        assertEquals(1234L, saved.totalAllocatedMinor)
        screen.recreate()
        awaitUi(screen) { controller.state.value is PersistentDataState.Ready }
        assertNull(controller.sourceCreationState.value)
        assertEquals(1, success(app.repository.snapshot(selected)).sources.size)
    }

    @Test fun editingPreviewAndWriteSurviveRecreateAndFailuresPreserveInput() = runBlocking {
        val period = success(app.repository.currentPeriod())
        success(app.repository.createSourceWithInitialAllocation("Original", period, 1000))
        val screen = launch()
        openBudget(screen)
        val controller = model(screen)
        screen.onActivity {
            (it.findViewById<android.widget.LinearLayout>(R.id.budgetSourceRows).getChildAt(0)).performClick()
        }
        awaitUi(screen) { rootWith(R.id.editSourceName) != null }
        val (releasePreview, previewLock) = gate()
        try {
            screen.onActivity {
                val root = rootWith(R.id.editSourceName)!!
                root.findViewById<EditText>(R.id.editSourceName).setText("Edited")
                root.findViewById<EditText>(R.id.sourceAdjustment).setText("99")
                root.findViewById<View>(R.id.sourceMinus).performClick()
                root.findViewById<EditText>(R.id.sourceAdjustment).setText("2")
            }
            screen.recreate()
            awaitUi(screen) { rootWith(R.id.editSourceName) != null }
            screen.onActivity {
                val root = rootWith(R.id.editSourceName)!!
                assertEquals("2", root.findViewById<EditText>(R.id.sourceAdjustment).text.toString())
                assertTrue(root.findViewById<View>(R.id.sourceMinus).isSelected)
                assertFalse(root.findViewById<View>(android.R.id.button1).isEnabled)
                assertNull(controller.sourceEditingState.value!!.previewResult)
            }
        } finally { releasePreview.complete(Unit); previewLock.join() }
        awaitUi(screen) {
            (controller.sourceEditingState.value?.previewResult as? DataResult.Success)?.value?.resultingAllocatedMinor == 800L
        }
        app.database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER stage7_fail_update BEFORE UPDATE ON budget_allocations
            BEGIN SELECT RAISE(ABORT, 'isolated fixture'); END
        """.trimIndent())
        screen.onActivity { rootWith(R.id.editSourceName)!!.findViewById<View>(android.R.id.button1).performClick() }
        awaitUi(screen) { controller.sourceEditingState.value?.submitResult is DataResult.Failure }
        screen.onActivity {
            val root = rootWith(R.id.editSourceName)!!
            assertEquals("Edited", root.findViewById<EditText>(R.id.editSourceName).text.toString())
            assertEquals("2", root.findViewById<EditText>(R.id.sourceAdjustment).text.toString())
            assertNotNull(root.findViewById<EditText>(R.id.sourceAdjustment).error)
        }
        assertEquals("Original", success(app.repository.snapshot(period)).sources.single().source.name)
        assertEquals(1000L, success(app.repository.snapshot(period)).totalAllocatedMinor)
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER stage7_fail_update")
        val (releaseWrite, writeLock) = gate()
        try {
            screen.onActivity { rootWith(R.id.editSourceName)!!.findViewById<View>(android.R.id.button1).performClick() }
            screen.recreate()
            awaitUi(screen) { rootWith(R.id.editSourceName) != null }
            screen.onActivity {
                val root = rootWith(R.id.editSourceName)!!
                assertFalse(root.findViewById<View>(android.R.id.button1).isEnabled)
                assertFalse(root.findViewById<View>(android.R.id.button2).isEnabled)
                assertNull(controller.submitSourceEditing())
            }
        } finally { releaseWrite.complete(Unit); writeLock.join() }
        awaitUi(screen) { controller.sourceEditingState.value == null }
        val saved = success(app.repository.snapshot(period))
        assertEquals("Edited", saved.sources.single().source.name)
        assertEquals(800L, saved.totalAllocatedMinor)
    }

    @Test fun rolloverAndTimezoneRefreshKeepHistoricalSelectionAndCapturedForm() = runBlocking {
        val old = success(app.repository.currentPeriod())
        val source = success(app.repository.createSourceWithInitialAllocation("Persistent", old, 500))
        val screen = launch()
        openBudget(screen)
        val controller = model(screen)
        screen.onActivity { controller.selectPeriod(old) }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        awaitUi(screen) { it.findViewById<View>(R.id.budgetAddSource).isEnabled &&
            (controller.state.value as? PersistentDataState.Ready)?.snapshot?.period == old }
        addForm(screen, "Captured", "7")
        app.clock.now = Instant.parse("2026-11-09T22:01:00Z") // Jerusalem: November 10, 00:01.
        dispatchDate(screen, Intent.ACTION_DATE_CHANGED)
        awaitUi(screen) {
            val home = controller.activeBudget.value as? PersistentDataState.Ready
            home?.snapshot?.period?.start != old.start
        }
        val home = (controller.activeBudget.value as PersistentDataState.Ready).snapshot
        assertEquals(0L, home.totalAllocatedMinor)
        assertEquals(source.id, home.sources.single().source.id)
        assertEquals(old, controller.selectedPeriod)
        assertEquals(old, controller.sourceCreationState.value!!.period)
        screen.onActivity { rootWith(R.id.addSourceName)!!.findViewById<View>(android.R.id.button1).performClick() }
        awaitUi(screen) { controller.sourceCreationState.value == null }
        assertEquals(1200L, success(app.repository.snapshot(old)).totalAllocatedMinor)
        assertEquals(0L, success(app.repository.snapshot()).totalAllocatedMinor)
        // Same instant, different injected zone. No device clock or zone is changed.
        app.clock.currentZone = ZoneId.of("America/Los_Angeles")
        dispatchDate(screen, Intent.ACTION_TIMEZONE_CHANGED)
        awaitUi(screen) {
            (controller.activeBudget.value as? PersistentDataState.Ready)?.snapshot?.period == old
        }
        assertEquals(old, controller.selectedPeriod)
    }

    @Test fun unknownOutcomeRestorationCannotResubmitAndReviewShowsCapturedPeriod() = runBlocking {
        val period = success(app.repository.currentPeriod())
        val source = success(app.repository.createSourceWithInitialAllocation("Already committed", period, 1200))
        val screen = launch()
        openBudget(screen)
        val controller = model(screen)
        val restored = SourceEditingState.fromBundle(SourceEditingState(source, period, 1000,
            name = "Already committed", adjustmentText = "2", submitting = true).toBundle())!!
        screen.onActivity { controller.restoreSourceEditing(restored) }
        awaitUi(screen) { rootWith(R.id.editSourceName) != null }
        screen.recreate()
        screen.onActivity {
            val root = rootWith(R.id.editSourceName)!!
            assertFalse(root.findViewById<View>(android.R.id.button1).isEnabled)
            assertFalse(root.findViewById<View>(R.id.sourceAdjustment).isEnabled)
            assertNull(controller.submitSourceEditing())
            assertTrue(root.findViewById<TextView>(R.id.sourcePreview).text.contains(
                it.getString(R.string.source_save_interrupted)))
            root.findViewById<View>(android.R.id.button2).performClick()
        }
        awaitUi(screen) { controller.sourceEditingState.value == null && controller.state.value is PersistentDataState.Ready }
        assertEquals(period, controller.selectedPeriod)
        assertEquals(1200L, success(app.repository.snapshot(period)).totalAllocatedMinor)
        val uncertainCreation = SourceCreationState.fromBundle(SourceCreationState(period,
            name = "Maybe committed", initialAmountText = "5", submitting = true).toBundle())!!
        screen.onActivity { controller.restoreSourceCreation(uncertainCreation) }
        awaitUi(screen) { rootWith(R.id.addSourceName) != null }
        screen.onActivity {
            val root = rootWith(R.id.addSourceName)!!
            assertFalse(root.findViewById<View>(android.R.id.button1).isEnabled)
            assertFalse(root.findViewById<View>(R.id.addSourceName).isEnabled)
            controller.updateSourceCreationName("Cannot erase uncertainty")
            assertEquals("Maybe committed", controller.sourceCreationState.value!!.name)
            assertNull(controller.submitSourceCreation())
            root.findViewById<View>(android.R.id.button2).performClick()
        }
        awaitUi(screen) { controller.sourceCreationState.value == null }
        assertEquals(1, success(app.repository.snapshot(period)).sources.size)
        val past = BudgetPeriod(java.time.LocalDate.of(1960, 11, 10), java.time.LocalDate.of(1960, 12, 10))
        assertEquals(past, SourceCreationState.fromBundle(SourceCreationState(past).toBundle())!!.period)
        assertEquals(past, SourceEditingState.fromBundle(SourceEditingState(source, past, 0).toBundle())!!.period)
    }

    @Test fun backPreservesHistoryAndLateNavigationCannotOverwriteNewSelection() = runBlocking {
        val screen = launch()
        openBudget(screen)
        val controller = model(screen)
        val active = success(app.repository.currentPeriod())
        val (release, lock) = gate()
        try {
            screen.onActivity {
                controller.selectNextPeriod()
                controller.selectPeriod(active)
            }
            awaitUi(screen) { controller.state.value is PersistentDataState.Loading }
        } finally { release.complete(Unit); lock.join() }
        awaitUi(screen) { (controller.state.value as? PersistentDataState.Ready)?.snapshot?.period == active }
        assertEquals(active, controller.selectedPeriod)
        screen.onActivity { controller.selectPreviousPeriod() }
        awaitUi(screen) {
            (controller.state.value as? PersistentDataState.Ready)?.snapshot?.period?.let { it != active } == true
        }
        val historical = controller.selectedPeriod!!
        screen.onActivity { it.findViewById<View>(R.id.menuButton).performClick() }
        awaitUi(screen) { rootWith(R.id.nav2) != null }
        screen.onActivity { rootWith(R.id.nav2)!!.findViewById<View>(R.id.nav2).performClick() }
        awaitUi(screen) { rootWith(R.id.nav2) == null && it.findViewById<View>(R.id.settingsPage).isShown }
        screen.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        awaitUi(screen) { it.findViewById<View>(R.id.budgetPage).isShown }
        assertEquals(historical, controller.selectedPeriod)
        screen.onActivity { it.findViewById<View>(R.id.menuButton).performClick() }
        awaitUi(screen) { rootWith(R.id.nav2) != null }
        screen.onActivity { rootWith(R.id.nav2)!!.findViewById<View>(R.id.nav2).performClick() }
        awaitUi(screen) { rootWith(R.id.nav2) == null && it.findViewById<View>(R.id.settingsPage).isShown }
        openBudget(screen)
        awaitUi(screen) {
            controller.selectedPeriod == null &&
                (controller.state.value as? PersistentDataState.Ready)?.snapshot?.period == active
        }
    }

    @Test fun localizedDialogsFitPhoneWidthAndExposeSelectedAdjustmentAndErrors() = runBlocking {
        val period = BudgetPeriod(java.time.LocalDate.of(2026, 12, 10), java.time.LocalDate.of(2027, 1, 10))
        val source = BudgetSource("local-calculator", "matrix", "Source")
        val screen = launch()
        for (language in listOf("he", "ru", "en")) for (night in listOf(false, true)) {
            // AppCompat dialogs inherit the Activity night mode. Use isolated appearance preferences
            // and recreate the real Activity instead of pretending a wrapper alone changes its theme.
            app.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit()
                .putBoolean("dark", night).commit()
            screen.recreate()
            awaitUi(screen) {
                it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            lateinit var form: SourceEditingDialog
            screen.onActivity { activity ->
                val locale = java.util.Locale.forLanguageTag(language)
                val config = Configuration(activity.resources.configuration).apply {
                    setLocale(locale); setLayoutDirection(locale)
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val themed = androidx.appcompat.view.ContextThemeWrapper(activity, R.style.AppTheme).apply {
                    applyOverrideConfiguration(config)
                }
                form = SourceEditingDialog(themed, {}, { _, _ -> }, {}, {})
                form.show()
                form.dialog.window!!.setLayout((320 * activity.resources.displayMetrics.density).toInt(),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                form.render(SourceEditingState(source, period, 1000, adjustmentText = "2", isAdd = false,
                    previewResult = DataResult.Success(BudgetAdjustmentPreview(source.id, period, 1000, -200, 800))))
            }
            awaitUi(screen) { form.dialog.findViewById<TextView>(R.id.editSourceMonth)?.layout != null }
            screen.onActivity {
                assertEquals(if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                    form.dialog.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                val label = form.dialog.findViewById<TextView>(R.id.editSourceMonth)!!
                assertTrue(label.text.contains("2026"))
                assertTrue(label.text.contains("2027"))
                assertTrue(label.contentDescription.contains("2026"))
                for (id in listOf(R.id.editSourceMonth, R.id.currentSourceBudget, R.id.sourcePreview)) {
                    val text = form.dialog.findViewById<TextView>(id)!!
                    assertTrue("$language/$night clipped height: " + text.text,
                        text.layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                    for (line in 0 until text.lineCount) assertTrue("$language/$night clipped width: " + text.text,
                        text.layout.getLineWidth(line) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1)
                }
                val minus = form.dialog.findViewById<View>(R.id.sourceMinus)!!
                val plus = form.dialog.findViewById<View>(R.id.sourcePlus)!!
                assertTrue(minus.createAccessibilityNodeInfo().isSelected)
                assertFalse(plus.createAccessibilityNodeInfo().isSelected)
                assertTrue(minus.contentDescription.isNotBlank())
                assertTrue(plus.contentDescription.isNotBlank())
                assertEquals(if (language == "he") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR,
                    label.layoutDirection)
                form.render(SourceEditingState(source, period, 1000, adjustmentText = "2",
                    previewResult = DataResult.Failure(DataError.StorageFailure(IllegalStateException("fixture")))))
                val preview = form.dialog.findViewById<TextView>(R.id.sourcePreview)!!
                assertEquals(form.dialog.context.getString(R.string.budget_preview_error), preview.text.toString())
                assertTrue(preview.isClickable)
                assertFalse(form.dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled)
                form.dispose()
            }
        }
    }

    @Test fun financialReadFailureOffersRetryAndKeepsSourceChoicesAndExpenseDraft() = runBlocking {
        val period = success(app.repository.currentPeriod())
        val source = success(app.repository.createSourceWithInitialAllocation("Available", period, 0))
        val screen = launch()
        val controller = model(screen)
        awaitUi(screen) { controller.activeBudget.value is PersistentDataState.Ready }
        screen.onActivity { it.findViewById<View>(R.id.addExpense).performClick() }
        awaitUi(screen) { rootWith(R.id.expenseNote) != null }
        screen.onActivity {
            val root = rootWith(R.id.expenseNote)!!
            root.findViewById<EditText>(R.id.amount).setText("7.89")
            root.findViewById<EditText>(R.id.expenseNote).setText("Keep note")
            root.findViewById<android.widget.LinearLayout>(R.id.expenseSourceChoices).getChildAt(0).performClick()
        }
        app.database.dao().insertExpense(com.example.manymanager.data.local.ExpenseEntity(
            source.calculatorId, "read-overflow-a", Long.MAX_VALUE, period.startEpochDay, null, source.id))
        app.database.dao().insertExpense(com.example.manymanager.data.local.ExpenseEntity(
            source.calculatorId, "read-overflow-b", 1, period.startEpochDay, null, source.id))
        awaitUi(screen) { controller.activeBudget.value is PersistentDataState.Failed }
        screen.recreate()
        awaitUi(screen) { rootWith(R.id.expenseNote) != null }
        screen.onActivity {
            val root = rootWith(R.id.expenseNote)!!
            assertEquals("7.89", root.findViewById<EditText>(R.id.amount).text.toString())
            assertEquals("Keep note", root.findViewById<EditText>(R.id.expenseNote).text.toString())
            assertTrue(root.findViewById<android.widget.LinearLayout>(R.id.expenseSourceChoices).getChildAt(0).isSelected)
            root.findViewById<View>(R.id.closeDialog).performClick()
        }
        awaitUi(screen) { rootWith(R.id.expenseNote) == null }
        screen.onActivity {
            assertEquals(it.getString(R.string.budget_load_error),
                it.findViewById<TextView>(R.id.homeBudgetRemaining).text.toString())
            assertEquals("—", it.findViewById<TextView>(R.id.homeTotal).text.toString())
            assertTrue(it.findViewById<View>(R.id.homeBudgetArea).isClickable)
        }
        // Remove only explicitly isolated test fixtures, then exercise the visible retry action.
        app.database.openHelper.writableDatabase.execSQL("DELETE FROM expenses WHERE id IN ('read-overflow-a', 'read-overflow-b')")
        screen.onActivity { it.findViewById<View>(R.id.homeBudgetArea).performClick() }
        awaitUi(screen) {
            (controller.activeBudget.value as? PersistentDataState.Ready)?.snapshot?.totalSpentMinor == 0L
        }
    }

    /** Protected system broadcasts cannot be sent by an app. Invoke the registered receiver's
     * callback with a simulated event; never change the user's device clock/zone. */
    private fun dispatchDate(screen: ActivityScenario<MainActivity>, action: String) {
        screen.onActivity {
            val field = MainActivity::class.java.getDeclaredField("dateChanges").apply { isAccessible = true }
            (field.get(it) as android.content.BroadcastReceiver).onReceive(it, Intent(action))
        }
    }

    private fun <T> success(result: DataResult<T>): T {
        assertTrue("Expected success, got $result", result is DataResult.Success)
        return (result as DataResult.Success).value
    }
}
