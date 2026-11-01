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
            home != null && home.snapshot.period.start != old.start
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

    @Test fun expenseSaveRecreationCategoryDraftAndAmountCorrection() = runBlocking {
        val period = success(app.repository.currentPeriod())
        val source = success(app.repository.createSourceWithInitialAllocation("Cash", period, 0))
        val screen = launch()
        val controller = model(screen)
        awaitUi(screen) { controller.sources.value is SourceListState.Ready && controller.categories.value is CategoryListState.Ready }
        screen.onActivity { it.findViewById<View>(R.id.addExpense).performClick() }
        awaitUi(screen) { rootWith(R.id.amount) != null }
        screen.onActivity {
            val root = rootWith(R.id.amount)!!
            root.findViewById<EditText>(R.id.amount).setText("12.34")
            root.findViewById<EditText>(R.id.expenseNote).setText("Keep note")
            root.findViewById<android.widget.LinearLayout>(R.id.expenseSourceChoices).getChildAt(0).performClick()
            root.findViewById<View>(R.id.expenseNewCategory).performClick()
        }
        awaitUi(screen) { rootWith(R.id.categoryNameInput) != null }
        screen.onActivity {
            rootWith(R.id.categoryNameInput)!!.findViewById<EditText>(R.id.categoryNameInput).setText("Food")
            controller.submitCategoryCreation()
        }
        awaitUi(screen) { controller.expenseCreationState.value?.draft?.categoryId != null && rootWith(R.id.categoryNameInput) == null }
        val draft = controller.expenseCreationState.value!!.draft
        assertEquals("12.34", draft.amount)
        assertEquals("Keep note", draft.note)
        assertEquals(source.id, draft.sourceId)
        assertNotNull(draft.date)
        screen.recreate()
        awaitUi(screen) { rootWith(R.id.amount) != null }
        assertEquals(draft, controller.expenseCreationState.value!!.draft)
        val (release, held) = gate()
        screen.onActivity {
            val save = rootWith(R.id.amount)!!.findViewById<View>(R.id.saveExpense)
            assertTrue(save.isEnabled)
            save.performClick(); save.performClick()
        }
        awaitUi(screen) { controller.expenseCreationState.value?.submitting == true }
        screen.recreate()
        awaitUi(screen) { rootWith(R.id.amount) != null }
        screen.onActivity {
            assertFalse(rootWith(R.id.amount)!!.findViewById<View>(R.id.saveExpense).isEnabled)
            assertNull(controller.submitExpenseCreation())
            controller.cancelExpenseCreation()
            assertTrue(controller.expenseCreationState.value!!.submitting)
        }
        screen.onActivity {
            val config = Configuration(it.resources.configuration)
            config.uiMode = config.uiMode xor Configuration.UI_MODE_NIGHT_MASK
            it.onConfigurationChanged(config)
        }
        awaitUi(screen) { rootWith(R.id.amount) != null }
        assertEquals(draft, controller.expenseCreationState.value!!.draft)
        release.complete(Unit); held.join()
        awaitUi(screen) { controller.expenseCreationState.value == null && rootWith(R.id.amount) == null }
        // New draft follows the actual current civil date, not the injected accounting clock.
        val savedPeriod = success(app.repository.periodContaining(draft.date!!))
        val saved = success(app.repository.snapshot(savedPeriod)).expenses.single()
        assertEquals(1234L, saved.amountMinor)
        assertEquals(draft.categoryId, saved.categoryId)
        // Render the real active list using the same date through the test application's boundary clock.
        app.clock.now = draft.date.atTime(12, 0).atZone(app.clock.zone).toInstant()
        screen.onActivity { controller.refresh() }
        awaitUi(screen) { (controller.activeBudget.value as? PersistentDataState.Ready)?.snapshot?.expenseCount == 1 }
        screen.onActivity {
            it.findViewById<View>(R.id.menuButton).performClick()
        }
        awaitUi(screen) { rootWith(R.id.nav1) != null }
        screen.onActivity { rootWith(R.id.nav1)!!.findViewById<View>(R.id.nav1).performClick() }
        awaitUi(screen) { it.findViewById<View>(R.id.summaryPage).isShown }
        screen.onActivity { it.findViewById<View>(R.id.allTab).performClick() }
        awaitUi(screen) { it.findViewById<android.widget.LinearLayout>(R.id.expenseRows).childCount == 1 }
        screen.onActivity { it.findViewById<android.widget.LinearLayout>(R.id.expenseRows).getChildAt(0).performClick() }
        awaitUi(screen) { rootWith(R.id.expenseCorrectionAmount) != null }
        screen.onActivity {
            rootWith(R.id.expenseCorrectionAmount)!!.findViewById<EditText>(R.id.expenseCorrectionAmount).setText("9.87")
            controller.cancelExpenseCorrection()
        }
        awaitUi(screen) { rootWith(R.id.expenseCorrectionAmount) == null }
        assertEquals(saved, success(app.repository.snapshot(savedPeriod)).expenses.single())
        screen.onActivity { it.findViewById<android.widget.LinearLayout>(R.id.expenseRows).getChildAt(0).performClick() }
        awaitUi(screen) { rootWith(R.id.expenseCorrectionAmount) != null }
        screen.onActivity { rootWith(R.id.expenseCorrectionAmount)!!.findViewById<EditText>(R.id.expenseCorrectionAmount).setText("9.87") }
        screen.recreate()
        awaitUi(screen) { rootWith(R.id.expenseCorrectionAmount) != null }
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_correction BEFORE UPDATE ON expenses BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        screen.onActivity {
            assertEquals("9.87", rootWith(R.id.expenseCorrectionAmount)!!.findViewById<EditText>(R.id.expenseCorrectionAmount).text.toString())
            controller.submitExpenseCorrection()
            assertNull(controller.submitExpenseCorrection())
        }
        awaitUi(screen) { controller.expenseCorrectionState.value?.error is DataError.StorageFailure }
        assertEquals(saved, success(app.repository.snapshot(savedPeriod)).expenses.single())
        assertEquals("9.87", controller.expenseCorrectionState.value!!.amountText)
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_correction")
        val (releaseCorrection, heldCorrection) = gate()
        try {
            screen.onActivity { controller.submitExpenseCorrection() }
            screen.recreate()
            awaitUi(screen) { rootWith(R.id.expenseCorrectionAmount) != null }
            screen.onActivity {
                assertFalse(rootWith(R.id.expenseCorrectionAmount)!!.findViewById<View>(android.R.id.button1).isEnabled)
                assertNull(controller.submitExpenseCorrection())
            }
        } finally { releaseCorrection.complete(Unit); heldCorrection.join() }
        awaitUi(screen) { controller.expenseCorrectionState.value == null }
        val corrected = saved.copy(amountMinor = 987)
        assertEquals(corrected, success(app.repository.snapshot(savedPeriod)).expenses.single())
        screen.onActivity {
            controller.restoreExpenseCorrection(ExpenseCorrectionState.fromBundle(
                ExpenseCorrectionState(corrected, "2", submitting = true).toBundle())!!)
        }
        awaitUi(screen) { rootWith(R.id.expenseCorrectionAmount) != null }
        screen.onActivity {
            assertFalse(rootWith(R.id.expenseCorrectionAmount)!!.findViewById<View>(android.R.id.button1).isEnabled)
            assertNull(controller.submitExpenseCorrection())
        }
        assertEquals(corrected, success(app.repository.snapshot(savedPeriod)).expenses.single())
    }

    @Test fun failedExpenseWritePreservesInputAndUnknownRestoreCannotSubmit() = runBlocking {
        val period = success(app.repository.currentPeriod())
        success(app.repository.createSourceWithInitialAllocation("Cash", period, 0))
        val screen = launch()
        val controller = model(screen)
        awaitUi(screen) { controller.sources.value is SourceListState.Ready }
        screen.onActivity { it.findViewById<View>(R.id.addExpense).performClick() }
        awaitUi(screen) { rootWith(R.id.amount) != null }
        screen.onActivity {
            val root = rootWith(R.id.amount)!!
            root.findViewById<EditText>(R.id.amount).setText("1.01")
            root.findViewById<EditText>(R.id.expenseNote).setText("retry")
            root.findViewById<android.widget.LinearLayout>(R.id.expenseSourceChoices).getChildAt(0).performClick()
        }
        val draft = controller.expenseCreationState.value!!.draft
        app.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_expense BEFORE INSERT ON expenses BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        screen.onActivity { controller.submitExpenseCreation() }
        awaitUi(screen) { controller.expenseCreationState.value?.error is DataError.StorageFailure }
        assertEquals(draft, controller.expenseCreationState.value!!.draft)
        screen.recreate()
        awaitUi(screen) { rootWith(R.id.amount) != null }
        assertEquals(draft, controller.expenseCreationState.value!!.draft)
        app.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_expense")
        screen.onActivity { controller.submitExpenseCreation() }
        awaitUi(screen) { controller.expenseCreationState.value == null && rootWith(R.id.amount) == null }
        app.clock.now = draft.date!!.atTime(12, 0).atZone(app.clock.zone).toInstant()
        screen.onActivity { controller.refresh() }
        awaitUi(screen) { (controller.activeBudget.value as? PersistentDataState.Ready)?.snapshot?.expenseCount == 1 }
        screen.onActivity {
            val row = it.findViewById<android.widget.LinearLayout>(R.id.expenseRows).getChildAt(0) as TextView
            assertTrue(row.text.contains(it.getString(R.string.uncategorized)))
            assertEquals("1.01", it.findViewById<TextView>(R.id.homeTotal).text.toString())
        }
        screen.onActivity {
            controller.restoreExpenseCreation(ExpenseCreationState.fromBundle(ExpenseCreationState(draft, submitting = true).toBundle()))
        }
        awaitUi(screen) { rootWith(R.id.expenseOperationStatus) != null }
        screen.onActivity {
            assertFalse(rootWith(R.id.amount)!!.findViewById<View>(R.id.saveExpense).isEnabled)
            assertNull(controller.submitExpenseCreation())
            assertTrue(rootWith(R.id.expenseOperationStatus)!!.findViewById<TextView>(R.id.expenseOperationStatus).text.isNotBlank())
        }
        assertEquals(1, success(app.repository.snapshot(success(app.repository.periodContaining(draft.date!!)))).expenseCount)
    }

    @Test fun summaryHomeManagementAndDraftConvergeOnSameCommittedFacts(): Unit = runBlocking {
        val period = success(app.repository.currentPeriod())
        val a = success(app.repository.createSourceWithInitialAllocation("Same", period, 500))
        val b = success(app.repository.createSourceWithInitialAllocation("Same", period, 0))
        val catA = success(app.repository.createCategory("Same"))
        val catB = success(app.repository.createCategory("Same"))
        val first = success(app.repository.createExpense(NewExpense(100, period.start, a.id, catA.id)))
        success(app.repository.createExpense(NewExpense(200, period.start, b.id, catB.id)))
        success(app.repository.createExpense(NewExpense(300, period.start, a.id)))
        val screen = launch()
        val controller = model(screen)
        fun rows(activity: MainActivity, id: Int) = activity.findViewById<android.widget.LinearLayout>(id)
        fun money(activity: MainActivity, value: Long) = activity.getString(R.string.ils_amount,
            BudgetAmounts.number(value, activity.resources.configuration.locales[0]))
        fun rowAmount(activity: MainActivity, container: Int, key: String?): String =
            (0 until rows(activity, container).childCount).map { rows(activity, container).getChildAt(it) }
                .single { it.tag == key }.findViewById<TextView>(R.id.breakdownAmount).text.toString()
        awaitUi(screen) { rows(it, R.id.categorySummaryRows).childCount == 3 && rows(it, R.id.sourceSummaryRows).childCount == 2 }
        screen.onActivity {
            assertEquals(money(it, 300), rowAmount(it, R.id.categorySummaryRows, null))
            assertEquals(money(it, 100), rowAmount(it, R.id.categorySummaryRows, catA.id))
            assertEquals(money(it, 200), rowAmount(it, R.id.categorySummaryRows, catB.id))
            assertEquals(money(it, 400), rowAmount(it, R.id.sourceSummaryRows, a.id))
            assertEquals(money(it, 200), rowAmount(it, R.id.sourceSummaryRows, b.id))
            assertEquals(3, rows(it, R.id.expenseRows).childCount)
            assertFalse(it.findViewById<View>(R.id.previousMonth).isEnabled)
            assertFalse(it.findViewById<View>(R.id.nextMonth).isEnabled)
        }
        success(app.repository.correctExpenseAmount(first.id, 400))
        awaitUi(screen) { rowAmount(it, R.id.categorySummaryRows, catA.id) == money(it, 400) }
        screen.onActivity {
            assertEquals(BudgetAmounts.number(900, it.resources.configuration.locales[0]), it.findViewById<TextView>(R.id.homeTotal).text.toString())
            assertEquals(money(it, 700), rowAmount(it, R.id.sourceSummaryRows, a.id))
            assertEquals(money(it, -400), it.findViewById<TextView>(R.id.homeBudgetRemaining).text.toString())
            assertEquals(3, rows(it, R.id.expenseRows).childCount)
        }
        success(app.repository.updateSourceAndAdjustAllocation(a.id, "Renamed", period, 100))
        awaitUi(screen) {
            val group = rows(it, R.id.sourceSummaryRows).getChildAt(0)
            group.findViewById<TextView>(R.id.breakdownName).text.toString() == "Renamed" &&
                (controller.state.value as? PersistentDataState.Ready)?.snapshot?.totalAllocatedMinor == 600L
        }
        screen.onActivity {
            assertEquals(money(it, 700), rowAmount(it, R.id.sourceSummaryRows, a.id))
            assertEquals(money(it, -300), it.findViewById<TextView>(R.id.homeBudgetRemaining).text.toString())
            assertEquals(money(it, 600), it.findViewById<TextView>(R.id.budgetAllocatedTotal).text.toString())
            val homeRows = rows(it, R.id.budgetBreakdownRows)
            assertTrue((homeRows.getChildAt(0) as android.widget.LinearLayout).getChildAt(0).let { view -> (view as TextView).text.contains("Renamed") })
            assertEquals(money(it, -100), ((homeRows.getChildAt(0) as android.widget.LinearLayout).getChildAt(1) as TextView).text.toString())
            assertEquals(money(it, -200), ((homeRows.getChildAt(1) as android.widget.LinearLayout).getChildAt(1) as TextView).text.toString())
            val list = rows(it, R.id.expenseRows)
            val correctedRow = (0 until list.childCount).map(list::getChildAt).single { view -> view.tag == first.id } as TextView
            assertTrue(correctedRow.text.contains(money(it, 400)))
            assertTrue(correctedRow.text.contains("Renamed"))
            it.findViewById<View>(R.id.addExpense).performClick()
        }
        awaitUi(screen) { rootWith(R.id.amount) != null }
        screen.onActivity {
            val root = rootWith(R.id.amount)!!
            root.findViewById<EditText>(R.id.amount).setText("1.23")
            root.findViewById<EditText>(R.id.expenseNote).setText("Keep")
            root.findViewById<android.widget.LinearLayout>(R.id.expenseSourceChoices).getChildAt(0).performClick()
        }
        val draft = controller.expenseCreationState.value!!.draft
        success(app.repository.renameSource(a.id, "Renamed again"))
        success(app.repository.createCategory("New choice"))
        screen.onActivity { controller.selectNextPeriod() }
        awaitUi(screen) {
            (controller.state.value as? PersistentDataState.Ready)?.snapshot?.period != period &&
                controller.state.value is PersistentDataState.Ready &&
                (controller.sources.value as? SourceListState.Ready)?.sources?.any { source -> source.name == "Renamed again" } == true
        }
        screen.onActivity {
            assertEquals(draft, controller.expenseCreationState.value!!.draft)
            assertEquals(money(it, 0), it.findViewById<TextView>(R.id.budgetAllocatedTotal).text.toString())
            assertEquals(money(it, 700), rowAmount(it, R.id.sourceSummaryRows, a.id))
            assertEquals(period, (controller.activeBudget.value as PersistentDataState.Ready).snapshot.period)
            val choices = rootWith(R.id.amount)!!.findViewById<android.widget.LinearLayout>(R.id.expenseSourceChoices)
            assertTrue((0 until choices.childCount).any { index -> (choices.getChildAt(index) as TextView).text.toString() == "Renamed again" })
        }
    }

    @Test fun financialPanelsOwnLoadingEmptyErrorAndIgnoreChoiceListFailures(): Unit = runBlocking {
        val period = success(app.repository.currentPeriod())
        val source = success(app.repository.createSourceWithInitialAllocation("Source", period, 0))
        val category = success(app.repository.createCategory("Category"))
        val screen = launch()
        val controller = model(screen)
        awaitUi(screen) { controller.activeBudget.value is PersistentDataState.Ready }
        val empty = success(app.repository.snapshot())
        success(app.repository.createExpense(NewExpense(29, period.start, source.id, category.id)))
        awaitUi(screen) { it.findViewById<android.widget.LinearLayout>(R.id.categorySummaryRows).childCount == 1 }
        val populated = success(app.repository.snapshot())
        screen.onActivity {
            var retries = 0
            val renderer = SummaryBreakdownUi(it.findViewById(R.id.summaryPage)) { retries++ }
            renderer.render(PersistentDataState.Loading)
            assertEquals(it.getString(R.string.expenses_loading), it.findViewById<TextView>(R.id.categorySummaryEmpty).text)
            assertEquals(0, it.findViewById<android.widget.LinearLayout>(R.id.sourceSummaryRows).childCount)
            renderer.render(PersistentDataState.Failed(DataError.StorageFailure(IllegalStateException("fixture"))))
            assertEquals(it.getString(R.string.expenses_load_error), it.findViewById<TextView>(R.id.sourceSummaryEmpty).text)
            it.findViewById<View>(R.id.sourceSummaryEmpty).performClick()
            assertEquals(1, retries)
            renderer.render(PersistentDataState.Ready(empty))
            assertEquals(it.getString(R.string.no_expenses), it.findViewById<TextView>(R.id.categorySummaryEmpty).text)
            renderer.render(PersistentDataState.Ready(populated))
            val categoryCallback = MainActivity::class.java.getDeclaredMethod("renderCategoryState", CategoryListState::class.java).apply { isAccessible = true }
            categoryCallback.invoke(it, CategoryListState.Failed(DataError.StorageFailure(IllegalStateException("choice fixture"))))
            val sourceCallback = MainActivity::class.java.getDeclaredMethod("renderSources", SourceListState::class.java).apply { isAccessible = true }
            sourceCallback.invoke(it, SourceListState.Failed(DataError.StorageFailure(IllegalStateException("choice fixture"))))
            assertEquals(View.GONE, it.findViewById<View>(R.id.categorySummaryEmpty).visibility)
            assertEquals(View.GONE, it.findViewById<View>(R.id.sourceSummaryEmpty).visibility)
            assertEquals(1, it.findViewById<android.widget.LinearLayout>(R.id.categorySummaryRows).childCount)
        }
    }

    @Test fun breakdownRowsWrapLongNamesAtPhoneWidthInAllLocalesAndThemes() = runBlocking {
        val period = success(app.repository.currentPeriod())
        val source = success(app.repository.createSourceWithInitialAllocation("Source", period, 0))
        val category = success(app.repository.createCategory("Category"))
        success(app.repository.createExpense(NewExpense(12345, period.start, source.id, category.id)))
        val snapshot = success(app.repository.snapshot())
        val screen = launch()
        for (language in listOf("he", "ru", "en")) for (night in listOf(false, true)) {
            app.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().putBoolean("dark", night).commit()
            screen.recreate()
            awaitUi(screen) { (it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
            lateinit var dialog: androidx.appcompat.app.AlertDialog
            lateinit var content: android.widget.LinearLayout
            val longName = when (language) { "he" -> "שם ארוך במיוחד ".repeat(12); "ru" -> "Очень длинное название ".repeat(12); else -> "Very long name ".repeat(12) }
            screen.onActivity { activity ->
                val config = Configuration(activity.resources.configuration).apply {
                    val locale = java.util.Locale.forLanguageTag(language)
                    setLocale(locale); setLayoutDirection(locale)
                }
                val themed = androidx.appcompat.view.ContextThemeWrapper(activity, R.style.AppTheme).apply { applyOverrideConfiguration(config) }
                content = android.widget.LinearLayout(themed).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    addView(android.view.LayoutInflater.from(themed).inflate(R.layout.panel_categories, this, false))
                    addView(android.view.LayoutInflater.from(themed).inflate(R.layout.panel_sources, this, false))
                }
                dialog = androidx.appcompat.app.AlertDialog.Builder(themed).setView(android.widget.ScrollView(themed).apply { addView(content) }).create()
                dialog.showWithRoundedSurface()
                dialog.window!!.setLayout((320 * activity.resources.displayMetrics.density).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                SummaryBreakdownUi(content) {}.render(PersistentDataState.Ready(snapshot.copy(
                    categories = listOf(category.copy(name = longName)),
                    sources = snapshot.sources.map { it.copy(source = it.source.copy(name = longName)) })))
            }
            awaitUi(screen) { content.findViewById<TextView>(R.id.breakdownName)?.layout != null }
            screen.onActivity {
                for (container in listOf(R.id.categorySummaryRows, R.id.sourceSummaryRows)) {
                    val row = content.findViewById<android.widget.LinearLayout>(container).getChildAt(0)
                    assertFalse(row.isClickable)
                    assertTrue(row.contentDescription.contains(longName))
                    for (id in listOf(R.id.breakdownName, R.id.breakdownAmount, R.id.breakdownCount)) {
                        val text = row.findViewById<TextView>(id)
                        assertTrue(text.layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                        for (line in 0 until text.lineCount) assertTrue(text.layout.getLineWidth(line) <= text.width - text.compoundPaddingLeft - text.compoundPaddingRight + 1)
                    }
                    assertEquals(if (language == "he") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR, row.layoutDirection)
                }
                dialog.dismiss()
            }
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
