package com.example.manymanager.data

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.manymanager.*
import com.example.manymanager.data.local.*
import com.example.manymanager.domain.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManyManagerRepositoryTest {
    private lateinit var database: ManyManagerDatabase
    private lateinit var repository: ManyManagerRepository
    private lateinit var initialization: CompletableDeferred<Result<CalculatorEntity>>
    private val owner = ManyManagerDao.LOCAL_CALCULATOR_ID
    private val clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC)
    private val period = BudgetPeriodCalculator(10, clock).currentPeriod()

    @Before fun open() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), ManyManagerDatabase::class.java
        ).build()
        initialization = CompletableDeferred(Result.success(database.dao().initializeLocalCalculator()))
        repository = ManyManagerRepository(database, initialization, clock)
    }

    @After fun close() { database.close() }

    @Test fun categoriesAreTrimmedObservedAndDuplicatesHaveDifferentIds() = runBlocking {
        assertTrue(success(repository.observeCategories().first()).isEmpty())
        val first = success(repository.createCategory("  Food  "))
        val second = success(repository.createCategory("Food"))
        assertEquals("Food", first.name)
        assertNotEquals(first.id, second.id)
        assertEquals(owner, first.calculatorId)
        assertEquals(listOf(first, second), success(repository.observeCategories().first()))
        assertEquals(DataError.BlankName, error(repository.createCategory(" \n ")))
    }

    @Test fun sourceAndAllocationAreCreatedTogetherAndObservedEvenWhenUnallocated() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation(" Cash ", period, 12345L))
        assertEquals("Cash", source.name)
        assertEquals(listOf(source), success(repository.observeSources().first()))
        assertEquals(12345L, snapshot().sources.single().allocatedMinor)
        val next = BudgetPeriodCalculator(10, clock).next(period)
        val unallocated = success(repository.snapshot(next)).sources.single()
        assertEquals(source, unallocated.source)
        assertEquals(0L, unallocated.allocatedMinor)
        assertEquals("Card", success(repository.renameSource(source.id, " Card ")).name)
    }

    @Test fun sourceCreationRollsBackWhenSecondWriteFails() = runBlocking {
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER test_reject_allocation BEFORE INSERT ON budget_allocations
            BEGIN SELECT RAISE(ABORT, 'test allocation failure'); END
        """.trimIndent())
        assertTrue(error(repository.createSourceWithInitialAllocation("Cash", period, 100))
            is DataError.StorageFailure)
        assertTrue(database.dao().sources(owner).isEmpty())
        assertTrue(database.dao().allocations(owner, period.id).isEmpty())
    }

    @Test fun sourceEditRollsBackRenameWhenAllocationWriteFails() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Before", period, 100))
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER test_reject_update BEFORE UPDATE ON budget_allocations
            BEGIN SELECT RAISE(ABORT, 'test update failure'); END
        """.trimIndent())
        assertTrue(error(repository.updateSourceAndAdjustAllocation(source.id, "After", period, 50))
            is DataError.StorageFailure)
        val state = snapshot()
        assertEquals("Before", state.sources.single().source.name)
        assertEquals(100L, state.totalAllocatedMinor)
    }

    @Test fun foreignAndMissingSourcesAreRejectedAndForeignDataIsExcluded() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO calculators VALUES ('foreign', 10, 'ILS')"
        )
        database.dao().insertSource(BudgetSourceEntity("foreign", "foreign-source", "Cash"))
        database.dao().insertCategory(CategoryEntity("foreign", "foreign-category", "Food"))
        database.dao().insertExpense(ExpenseEntity("foreign", "foreign-expense", 500,
            period.startEpochDay, "foreign-category", "foreign-source"))
        for (id in listOf("foreign-source", "missing")) {
            assertEquals(DataError.MissingEntity(EntityKind.SOURCE, id),
                error(repository.renameSource(id, "Renamed")))
            assertEquals(DataError.MissingEntity(EntityKind.SOURCE, id),
                error(repository.updateSourceAndAdjustAllocation(id, "Renamed", period, 1)))
            assertEquals(DataError.MissingEntity(EntityKind.SOURCE, id),
                error(repository.previewBudgetAdjustment(id, period, 1)))
        }
        assertTrue(success(repository.observeCategories().first()).isEmpty())
        assertTrue(success(repository.observeSources().first()).isEmpty())
        assertEquals(0, snapshot().expenseCount)
    }

    @Test fun previewDoesNotWriteAndConfirmUsesLatestAllocation() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 100))
        val preview = success(repository.previewBudgetAdjustment(source.id, period, 10))
        assertEquals(110L, preview.resultingAllocatedMinor)
        assertEquals(100L, snapshot().totalAllocatedMinor)
        success(repository.updateSourceAndAdjustAllocation(source.id, "Card", period, 50))
        val confirmed = success(repository.updateSourceAndAdjustAllocation(source.id, "Final", period, 10))
        assertEquals(150L, confirmed.currentAllocatedMinor)
        assertEquals(160L, confirmed.resultingAllocatedMinor)
        assertEquals("Final", snapshot().sources.single().source.name)
        assertEquals(0, snapshot().expenseCount)
    }

    @Test fun signedPreviewIsAllowedButNegativeAllocationAndBlankRenameRollback() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 100))
        assertEquals(-50L, success(repository.previewBudgetAdjustment(source.id, period, -150))
            .resultingAllocatedMinor)
        assertEquals(DataError.NegativeAllocatedBudget,
            error(repository.updateSourceAndAdjustAllocation(source.id, "Changed", period, -150)))
        assertEquals(DataError.BlankName,
            error(repository.updateSourceAndAdjustAllocation(source.id, "  ", period, 10)))
        assertEquals("Cash", snapshot().sources.single().source.name)
        assertEquals(100L, snapshot().totalAllocatedMinor)
        assertEquals(DataError.NegativeAllocatedBudget,
            error(repository.createSourceWithInitialAllocation("Negative", period, -1)))
        assertEquals(1, snapshot().sources.size)
    }

    @Test fun concurrentAdjustmentsDoNotLoseUpdatesAndOtherPeriodsStayUntouched() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 100))
        val next = BudgetPeriodCalculator(10, clock).next(period)
        success(repository.updateSourceAndAdjustAllocation(source.id, "Cash", next, 300))
        coroutineScope {
            (1..30).map {
                async { success(repository.updateSourceAndAdjustAllocation(source.id, "Cash", period, 1)) }
            }.awaitAll()
        }
        assertEquals(130L, snapshot().totalAllocatedMinor)
        assertEquals(300L, success(repository.snapshot(next)).totalAllocatedMinor)
        assertEquals(0, snapshot().expenseCount)
    }

    @Test fun calculatorBoundariesAndUncategorizedNegativeRemainingAreRespected() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 0))
        val category = success(repository.createCategory("Food"))
        fixtureExpense("before", 999, period.start.minusDays(1), source.id)
        fixtureExpense("start", 100, period.start, source.id, category.id)
        fixtureExpense("last", 200, period.endExclusive.minusDays(1), source.id)
        fixtureExpense("excluded", 999, period.endExclusive, source.id)
        val snapshot = snapshot()
        assertEquals(300L, snapshot.totalSpentMinor)
        assertEquals(-300L, snapshot.remainingMinor)
        assertEquals(-300L, snapshot.sources.single().remainingMinor)
        assertEquals(2, snapshot.expenseCount)
        success(repository.updateSourceAndAdjustAllocation(source.id, "Cash", period, 10))
        assertEquals(-290L, snapshot().remainingMinor)
        assertEquals(CategorySummary(null, 200, 1),
            snapshot.categoryBreakdown.single { it.categoryId == null })
        assertEquals(setOf("start", "last"), snapshot.expenses.map { it.id }.toSet())
        assertEquals(999L, success(repository.snapshot(
            BudgetPeriodCalculator(10, clock).next(period))).totalSpentMinor)
    }

    @Test fun savedSettingsAreReadWithoutOverwritingAndIncompatiblePeriodsFail() = runBlocking {
        val invalid = BudgetPeriod(period.start, period.endExclusive.plusDays(1))
        assertEquals(DataError.InvalidPeriod, error(repository.snapshot(invalid)))
        assertEquals(DataError.InvalidPeriod,
            error(repository.createSourceWithInitialAllocation("Cash", invalid, 0)))
        database.openHelper.writableDatabase.execSQL(
            "UPDATE calculators SET periodStartDay = 15, currencyCode = 'EUR' WHERE id = ?",
            arrayOf(owner)
        )
        assertEquals(DataError.InvalidPeriod, error(repository.snapshot(period)))
        val newPeriod = success(repository.currentPeriod())
        assertEquals(LocalDate.of(2026, 11, 15), newPeriod.start)
        assertEquals("EUR", success(repository.snapshot(newPeriod)).calculator.currencyCode)
        assertEquals(success(repository.periodContaining(LocalDate.of(2026, 11, 14))).endExclusive,
            newPeriod.start)
    }

    @Test fun adjustmentAndTotalOverflowFailWithoutPartialWrites() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Maximum", period, Long.MAX_VALUE))
        assertEquals(DataError.AmountOverflow,
            error(repository.previewBudgetAdjustment(source.id, period, 1)))
        assertEquals(DataError.AmountOverflow,
            error(repository.updateSourceAndAdjustAllocation(source.id, "Changed", period, 1)))
        assertEquals(DataError.AmountOverflow,
            error(repository.createSourceWithInitialAllocation("Another", period, 1)))
        assertEquals("Maximum", snapshot().sources.single().source.name)
        val zero = success(repository.createSourceWithInitialAllocation("Zero", period, 0))
        assertEquals(DataError.AmountOverflow,
            error(repository.updateSourceAndAdjustAllocation(zero.id, "Changed", period, 1)))
        assertEquals(2, snapshot().sources.size)
    }

    @Test fun expenseOverflowIsTypedRatherThanWrappedOrRounded() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 0))
        fixtureExpense("one", Long.MAX_VALUE, period.start, source.id)
        fixtureExpense("two", 1, period.start, source.id)
        assertEquals(DataError.AmountOverflow, error(repository.snapshot(period)))
        fixtureExpense("three", -1, period.start, source.id)
        assertEquals(Long.MAX_VALUE, snapshot().totalSpentMinor)
    }

    @Test fun snapshotsObserveOnlyConsistentCommittedRevisions() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("v0", period, 100))
        fixtureExpense("spent", 0, period.start, source.id)
        coroutineScope {
            val emitted = Channel<DataResult<BudgetSnapshot>>(Channel.UNLIMITED)
            val collector = repository.observeSnapshot(period).onEach { emitted.send(it) }.launchIn(this)
            try {
                withTimeout(10_000) { success(emitted.receive()) }
                for (index in 1..15) {
                    // Fixture-only transaction changes all relevant tables as one revision.
                    database.withTransaction {
                        database.dao().renameSource(owner, source.id, "v$index")
                        database.dao().putAllocation(BudgetAllocationEntity(owner, source.id, period.id,
                            100L + index))
                        database.openHelper.writableDatabase.execSQL(
                            "UPDATE expenses SET amountMinor = ? WHERE calculatorId = ? AND id = 'spent'",
                            arrayOf<Any>(index, owner)
                        )
                    }
                }
                withTimeout(10_000) {
                    while (true) {
                        val snapshot = success(emitted.receive())
                        val version = snapshot.sources.single().source.name.removePrefix("v").toLong()
                        assertEquals(100L + version, snapshot.totalAllocatedMinor)
                        assertEquals(version, snapshot.totalSpentMinor)
                        assertEquals(100L, snapshot.remainingMinor)
                        if (version == 15L) break
                    }
                }
            } finally { collector.cancelAndJoin() }
        }
    }

    @Test fun initializationFailureIsNotAnEmptySuccessAndCancellationIsPreserved() = runBlocking {
        val cause = IllegalStateException("test initialization")
        val failed = ManyManagerRepository(database, CompletableDeferred(Result.failure(cause)), clock)
        assertSame(cause, (error(failed.createCategory("Food")) as DataError.InitializationFailure).cause)
        assertSame(cause, (error(failed.observeSnapshot().first()) as DataError.InitializationFailure).cause)
        val waiting = CompletableDeferred<Result<CalculatorEntity>>()
        val pending = ManyManagerRepository(database, waiting, clock)
        val operation = async { pending.createCategory("Cancelled") }
        yield()
        operation.cancelAndJoin()
        assertTrue(operation.isCancelled)
        assertTrue(database.dao().categories(owner).isEmpty())
        val cancelled = ManyManagerRepository(database,
            CompletableDeferred(Result.failure(CancellationException("test cancellation"))), clock)
        try {
            cancelled.snapshot()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun storageFailureRetainsCause() = runBlocking {
        database.close()
        assertTrue(error(repository.snapshot()) is DataError.StorageFailure)
    }

    @Test fun activePeriodRefreshConsultsClockInsteadOfCachingToday() = runBlocking {
        val moving = object : Clock() {
            var value = Instant.parse("2026-11-09T23:59:59Z")
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = fixed(value, zone)
            override fun instant() = value
        }
        val repository = ManyManagerRepository(database, initialization, moving)
        assertEquals(LocalDate.of(2026, 10, 10), success(repository.currentPeriod()).start)
        moving.value = Instant.parse("2026-11-10T00:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 10), success(repository.observeSnapshot().first()).period.start)
    }

    @Test fun viewModelFactoryKeepsStateAndMutationOutcomesSeparateAndCancelsOnClear() = runBlocking {
        val store = ViewModelStore()
        val factory = PersistentDataViewModelFactory(repository)
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(store, factory)[PersistentDataViewModel::class.java]
        }
        assertEquals(PersistentDataState.Loading, model.state.value)
        val states = Channel<PersistentDataState>(Channel.UNLIMITED)
        val collection = model.state.onEach { states.send(it) }.launchIn(this)
        try {
            withTimeout(10_000) { model.state.first { it is PersistentDataState.Ready } }
            withContext(Dispatchers.Main) {
                model.beginCategoryCreation(false)
                model.updateCategoryName("Food")
                model.submitCategoryCreation()?.join()
            }
            val category = checkNotNull((model.categoryCreation.value?.result as? DataResult.Success)?.value)
            withTimeout(10_000) {
                model.state.first {
                    it is PersistentDataState.Ready && it.snapshot.categories.any { row -> row.id == category.id }
                }
            }
            val waiting = CompletableDeferred<Result<CalculatorEntity>>()
            val pendingModel = withContext(Dispatchers.Main) {
                ViewModelProvider(store, PersistentDataViewModelFactory(
                    ManyManagerRepository(database, waiting, clock)
                ))["pending", PersistentDataViewModel::class.java]
            }
            val job = withContext(Dispatchers.Main) {
                pendingModel.beginCategoryCreation(false)
                pendingModel.updateCategoryName("Cancelled")
                pendingModel.submitCategoryCreation()
            }
            withContext(Dispatchers.Main) { store.clear() }
            job?.join()
            assertTrue(job?.isCancelled == true)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            collection.cancelAndJoin()
        }
    }

    @Test fun viewModelCategoryStateStreamCreationFlowAndBusyGuard() = runBlocking {
        val store = ViewModelStore()
        val factory = PersistentDataViewModelFactory(repository)
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(store, factory)[PersistentDataViewModel::class.java]
        }
        val categoriesChannel = Channel<CategoryListState>(Channel.UNLIMITED)
        val collection = model.categories.onEach { categoriesChannel.send(it) }.launchIn(this)
        try {
            val initial = withTimeout(10_000) {
                model.categories.first { it is CategoryListState.Ready }
            } as CategoryListState.Ready
            assertTrue(initial.categories.isEmpty())

            withContext(Dispatchers.Main) {
                model.beginCategoryCreation(fromExpense = true)
                model.updateCategoryName("Shopping")
            }
            assertEquals("Shopping", model.categoryCreation.value?.name)
            assertTrue(model.categoryCreation.value?.fromExpense == true)

            val job = withContext(Dispatchers.Main) { model.submitCategoryCreation() }
            assertNotNull(job)
            // Synchronous busy guard prevents second submission
            assertNull(withContext(Dispatchers.Main) { model.submitCategoryCreation() })

            job?.join()
            val createdState = model.categoryCreation.value
            assertNotNull(createdState)
            val createdCategory = (createdState?.result as? DataResult.Success)?.value
            assertNotNull(createdCategory)
            assertEquals("Shopping", createdCategory?.name)

            val readyState = withTimeout(10_000) {
                model.categories.first {
                    it is CategoryListState.Ready && it.categories.any { c -> c.id == createdCategory?.id }
                }
            } as CategoryListState.Ready
            assertEquals(1, readyState.categories.size)
            assertEquals(createdCategory?.id, readyState.categories.single().id)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            collection.cancelAndJoin()
        }
    }

    @Test fun expenseDraftAndCategoryCreationStateBundleSaveRestore() {
        val draft = ExpenseDraft("150.00", LocalDate.of(2026, 11, 10), "Groceries", "cat-123", "src-456")
        val bundle = draft.toBundle()
        val restoredDraft = ExpenseDraft.fromBundle(bundle)
        assertEquals("150.00", restoredDraft.amount)
        assertEquals(LocalDate.of(2026, 11, 10), restoredDraft.date)
        assertEquals("Groceries", restoredDraft.note)
        assertEquals("cat-123", restoredDraft.categoryId)
        assertEquals("src-456", restoredDraft.sourceId)

        val creation = CategoryCreationState(fromExpense = true, name = "Food", submitting = true)
        val creationBundle = creation.toBundle()
        val restoredCreation = CategoryCreationState.fromBundle(creationBundle)
        assertTrue(restoredCreation.fromExpense)
        assertEquals("Food", restoredCreation.name)
        assertTrue(restoredCreation.interrupted)
    }

    @Test fun viewModelSourceCreationEditingAndPeriodNavigation() = runBlocking {
        val store = ViewModelStore()
        val factory = PersistentDataViewModelFactory(repository)
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(store, factory)[PersistentDataViewModel::class.java]
        }
        val statesChannel = Channel<PersistentDataState>(Channel.UNLIMITED)
        val collection = model.state.onEach { statesChannel.send(it) }.launchIn(this)
        try {
            val initial = withTimeout(10_000) {
                model.state.first { it is PersistentDataState.Ready }
            } as PersistentDataState.Ready
            val currentPeriod = initial.snapshot.period

            withContext(Dispatchers.Main) {
                model.beginSourceCreation(currentPeriod)
                model.updateSourceCreationName("Cash")
                model.updateSourceCreationAmount("250.00")
            }
            val createJob = withContext(Dispatchers.Main) { model.submitSourceCreation() }
            assertNotNull(createJob)
            createJob?.join()

            val createdSource = (model.sourceCreationState.value?.result as? DataResult.Success)?.value
            assertNotNull(createdSource)
            assertEquals("Cash", createdSource?.name)

            val readyState = withTimeout(10_000) {
                model.state.first {
                    it is PersistentDataState.Ready && it.snapshot.sources.any { s -> s.source.id == createdSource?.id }
                }
            } as PersistentDataState.Ready
            val sourceBudget = readyState.snapshot.sources.single { it.source.id == createdSource?.id }
            assertEquals(25000L, sourceBudget.allocatedMinor)

            withContext(Dispatchers.Main) {
                model.beginSourceEditing(sourceBudget.source, currentPeriod, sourceBudget.allocatedMinor)
                model.updateSourceEditingName("Main Cash")
                model.updateSourceEditingAdjustment("50.00", true)
            }
            val preview = withTimeout(10_000) {
                model.sourceEditingState.first { it?.previewResult is DataResult.Success }
            }?.previewResult as DataResult.Success
            assertEquals(30000L, preview.value.resultingAllocatedMinor)

            val editJob = withContext(Dispatchers.Main) { model.submitSourceEditing() }
            assertNotNull(editJob)
            editJob?.join()

            val updatedState = withTimeout(10_000) {
                model.state.first {
                    it is PersistentDataState.Ready && it.snapshot.sources.any { s -> s.source.name == "Main Cash" && s.allocatedMinor == 30000L }
                }
            } as PersistentDataState.Ready
            assertEquals(30000L, updatedState.snapshot.totalAllocatedMinor)

            withContext(Dispatchers.Main) { model.selectNextPeriod() }
            val nextState = withTimeout(10_000) {
                model.state.first { it is PersistentDataState.Ready && it.snapshot.period != currentPeriod }
            } as PersistentDataState.Ready
            assertEquals(0L, nextState.snapshot.totalAllocatedMinor)

            withContext(Dispatchers.Main) { model.selectPreviousPeriod() }
            val prevState = withTimeout(10_000) {
                model.state.first { it is PersistentDataState.Ready && it.snapshot.period == currentPeriod }
            } as PersistentDataState.Ready
            assertEquals(30000L, prevState.snapshot.totalAllocatedMinor)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            collection.cancelAndJoin()
        }
    }

    @Test fun sourceCreationAndEditingStateBundleSaveRestore() {
        val creationState = SourceCreationState(period = period, name = "Bank", initialAmountText = "100.00", submitting = true)
        val creationBundle = creationState.toBundle()
        val restoredCreation = checkNotNull(SourceCreationState.fromBundle(creationBundle))
        assertEquals(period, restoredCreation.period)
        assertEquals("Bank", restoredCreation.name)
        assertEquals("100.00", restoredCreation.initialAmountText)
        assertTrue(restoredCreation.interrupted)

        val source = BudgetSource(owner, "src-1", "Bank")
        val editingState = SourceEditingState(source = source, period = period, currentAllocationMinor = 10000L, name = "Main Bank", adjustmentText = "20.00", isAdd = false, submitting = true)
        val editingBundle = editingState.toBundle()
        val restoredEditing = checkNotNull(SourceEditingState.fromBundle(editingBundle))
        assertEquals(source.id, restoredEditing.source.id)
        assertEquals(period, restoredEditing.period)
        assertEquals(10000L, restoredEditing.currentAllocationMinor)
        assertEquals("Main Bank", restoredEditing.name)
        assertEquals("20.00", restoredEditing.adjustmentText)
        assertFalse(restoredEditing.isAdd)
        assertTrue(restoredEditing.interrupted)
    }

    @Test fun unsupportedSavedStartDayIsReportedAndNeverRewritten() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE calculators SET periodStartDay = 30 WHERE id = ?", arrayOf(owner)
        )
        assertEquals(DataError.InvalidPeriod, error(repository.currentPeriod()))
        assertEquals(DataError.InvalidPeriod, error(repository.snapshot()))
        assertEquals(30, database.dao().calculator(owner)!!.periodStartDay)
    }

    @Test fun operationsWaitForInitializationAndDoNotCreateDataEarly() = runBlocking {
        val waiting = CompletableDeferred<Result<CalculatorEntity>>()
        val pending = ManyManagerRepository(database, waiting, clock)
        val creation = async(start = CoroutineStart.UNDISPATCHED) { pending.createCategory("Food") }
        assertFalse(creation.isCompleted)
        assertTrue(database.dao().categories(owner).isEmpty())
        waiting.complete(Result.success(database.dao().calculator(owner)!!))
        assertEquals("Food", success(withTimeout(10_000) { creation.await() }).name)
    }

    @Test fun categoryObserverUpdatesAndSnapshotCanRecoverFromOverflow() = runBlocking {
        coroutineScope {
            val categories = Channel<DataResult<List<Category>>>(Channel.UNLIMITED)
            val observer = repository.observeCategories().onEach { categories.send(it) }.launchIn(this)
            try {
                assertTrue(success(withTimeout(10_000) { categories.receive() }).isEmpty())
                val created = success(repository.createCategory("Food"))
                assertEquals(listOf(created),
                    success(withTimeout(10_000) { categories.receive() }))
            } finally { observer.cancelAndJoin() }
        }
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 0))
        fixtureExpense("huge", Long.MAX_VALUE, period.start, source.id)
        fixtureExpense("extra", 1, period.start, source.id)
        coroutineScope {
            val states = Channel<DataResult<BudgetSnapshot>>(Channel.UNLIMITED)
            val observer = repository.observeSnapshot(period).onEach { states.send(it) }.launchIn(this)
            try {
                assertEquals(DataError.AmountOverflow,
                    error(withTimeout(10_000) { states.receive() }))
                fixtureExpense("offset", -1, period.start, source.id)
                assertEquals(Long.MAX_VALUE,
                    success(withTimeout(10_000) { states.receive() }).totalSpentMinor)
            } finally { observer.cancelAndJoin() }
        }
    }

    @Test fun viewModelReportsInitializationFailureAndRejectsUnknownModelTypes() = runBlocking<Unit> {
        val cause = IllegalStateException("initialization fixture")
        val store = ViewModelStore()
        val factory = PersistentDataViewModelFactory(ManyManagerRepository(
            database, CompletableDeferred(Result.failure(cause)), clock
        ))
        try {
            val model = withContext(Dispatchers.Main) {
                ViewModelProvider(store, factory)[PersistentDataViewModel::class.java]
            }
            val failed = withTimeout(10_000) {
                model.state.first { it is PersistentDataState.Failed }
            } as PersistentDataState.Failed
            assertSame(cause, (failed.error as DataError.InitializationFailure).cause)
            assertThrows(IllegalArgumentException::class.java) { factory.create(ViewModel::class.java) }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun viewModelSelectsPeriodsAndRefreshesTodayFromClock() = runBlocking {
        val moving = object : Clock() {
            var value = clock.instant()
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId): Clock = fixed(value, zone)
            override fun instant() = value
        }
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(store, PersistentDataViewModelFactory(
                ManyManagerRepository(database, initialization, moving)
            ))[PersistentDataViewModel::class.java]
        }
        val collection = model.state.launchIn(this)
        try {
            withTimeout(10_000) { model.state.first {
                it is PersistentDataState.Ready && it.snapshot.period == period
            } }
            val next = BudgetPeriodCalculator(10, moving).next(period)
            withContext(Dispatchers.Main) { model.selectPeriod(next) }
            withTimeout(10_000) { model.state.first {
                it is PersistentDataState.Ready && it.snapshot.period == next
            } }
            withContext(Dispatchers.Main) { model.selectPeriod(null) }
            withTimeout(10_000) { model.state.first {
                it is PersistentDataState.Ready && it.snapshot.period == period
            } }
            moving.value = Instant.parse("2026-12-10T12:00:00Z")
            withContext(Dispatchers.Main) { model.refresh() }
            withTimeout(10_000) { model.state.first {
                it is PersistentDataState.Ready && it.snapshot.period == next
            } }
            Unit
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            collection.cancelAndJoin()
        }
    }

    @Test fun sourceFormsGuardPendingWritesPreserveFailuresAndCancelWithoutWriting() = runBlocking {
        val waiting = CompletableDeferred<Result<CalculatorEntity>>()
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(store, PersistentDataViewModelFactory(
                ManyManagerRepository(database, waiting, clock)
            ))[PersistentDataViewModel::class.java]
        }
        try {
            val pending = withContext(Dispatchers.Main) {
                model.beginSourceCreation(period)
                model.updateSourceCreationName(" Cash ")
                model.updateSourceCreationAmount("12.34")
                val first = model.submitSourceCreation()
                assertNull(model.submitSourceCreation())
                model.cancelSourceCreation()
                assertTrue(model.sourceCreationState.value!!.submitting)
                // Reattaching/restoring a window must not replace or resubmit its pending state.
                model.restoreSourceCreation(SourceCreationState(period, "Wrong", "99"))
                first!!
            }
            waiting.complete(Result.success(database.dao().calculator(owner)!!))
            pending.join()
            val source = success(model.sourceCreationState.value!!.result!!)
            assertEquals(1234L, snapshot().totalAllocatedMinor)
            withContext(Dispatchers.Main) {
                assertNull(model.submitSourceCreation())
                model.acknowledgeSourceCreation()
                model.beginSourceEditing(source, period, 1234)
                model.updateSourceEditingName("Cancelled")
                model.updateSourceEditingAdjustment("1", false)
                model.cancelSourceEditing()
            }
            assertEquals("Cash", snapshot().sources.single().source.name)
            assertEquals(1234L, snapshot().totalAllocatedMinor)

            database.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER form_fail BEFORE INSERT ON budget_allocations
                BEGIN SELECT RAISE(ABORT, 'fixture'); END
            """.trimIndent())
            val failed = withContext(Dispatchers.Main) {
                model.beginSourceCreation(period)
                model.updateSourceCreationName("Retry me")
                model.updateSourceCreationAmount("7.89")
                model.submitSourceCreation()!!
            }
            failed.join()
            assertTrue(model.sourceCreationState.value!!.result is DataResult.Failure)
            assertEquals("Retry me", model.sourceCreationState.value!!.name)
            assertEquals("7.89", model.sourceCreationState.value!!.initialAmountText)
            assertEquals(1, snapshot().sources.size)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER form_fail")
            withContext(Dispatchers.Main) { model.submitSourceCreation()!! }.join()
            assertEquals(2, snapshot().sources.size)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun latestPreviewDisablesInvalidInputAndAdjustmentCannotBeAppliedTwice() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 1000))
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(store, PersistentDataViewModelFactory(repository))[PersistentDataViewModel::class.java]
        }
        try {
            withContext(Dispatchers.Main) {
                model.beginSourceEditing(source, period, 1000)
                model.updateSourceEditingAdjustment("3", true)
            }
            withTimeout(10_000) { model.sourceEditingState.first {
                (it?.previewResult as? DataResult.Success)?.value?.resultingAllocatedMinor == 1300L
            } }
            withContext(Dispatchers.Main) {
                model.updateSourceEditingAdjustment("999", false)
                assertNull(model.sourceEditingState.value!!.previewResult)
                assertNull(model.submitSourceEditing())
                model.updateSourceEditingAdjustment("invalid", true)
                assertTrue(model.sourceEditingState.value!!.previewResult is DataResult.Failure)
                assertNull(model.submitSourceEditing())
                model.updateSourceEditingAdjustment("2", false)
            }
            withTimeout(10_000) { model.sourceEditingState.first {
                (it?.previewResult as? DataResult.Success)?.value?.resultingAllocatedMinor == 800L
            } }
            database.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER edit_form_fail BEFORE UPDATE ON budget_allocations
                BEGIN SELECT RAISE(ABORT, 'fixture'); END
            """.trimIndent())
            val job = withContext(Dispatchers.Main) {
                model.updateSourceEditingName("Renamed")
                val pending = model.submitSourceEditing()!!
                assertNull(model.submitSourceEditing())
                pending
            }
            job.join()
            val failed = model.sourceEditingState.value!!
            assertTrue(failed.submitResult is DataResult.Failure)
            assertEquals("Renamed", failed.name)
            assertEquals("2", failed.adjustmentText)
            assertFalse(failed.isAdd)
            assertEquals("Cash", snapshot().sources.single().source.name)
            assertEquals(1000L, snapshot().totalAllocatedMinor)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER edit_form_fail")
            withContext(Dispatchers.Main) { model.submitSourceEditing()!! }.join()
            withContext(Dispatchers.Main) { assertNull(model.submitSourceEditing()) }
            assertEquals(800L, snapshot().totalAllocatedMinor)
            assertEquals(source.id, snapshot().sources.single().source.id)
            val restored = SourceEditingState.fromBundle(model.sourceEditingState.value!!.toBundle())!!
            assertTrue(restored.submitResult is DataResult.Success)
        } finally { withContext(Dispatchers.Main) { store.clear() } }
    }

    @Test fun activeBudgetAndSourceChoicesRemainIndependentOfHistoricalSelectionAndOverflow() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 400))
        val next = BudgetPeriodCalculator(10, clock).next(period)
        val store = ViewModelStore()
        val model = withContext(Dispatchers.Main) {
            ViewModelProvider(store, PersistentDataViewModelFactory(repository))[PersistentDataViewModel::class.java]
        }
        val active = model.activeBudget.launchIn(this)
        val selected = model.state.launchIn(this)
        val sources = model.sources.launchIn(this)
        try {
            withContext(Dispatchers.Main) { model.selectPeriod(next) }
            withTimeout(10_000) { model.state.first {
                it is PersistentDataState.Ready && it.snapshot.period == next && it.snapshot.totalAllocatedMinor == 0L
            } }
            val home = withTimeout(10_000) {
                model.activeBudget.first { it is PersistentDataState.Ready }
            } as PersistentDataState.Ready
            assertEquals(period, home.snapshot.period)
            assertEquals(400L, home.snapshot.totalAllocatedMinor)
            fixtureExpense("overflow-a", Long.MAX_VALUE, period.start, source.id)
            fixtureExpense("overflow-b", 1, period.start, source.id)
            withTimeout(10_000) { model.activeBudget.first { it is PersistentDataState.Failed } }
            val choices = withTimeout(10_000) {
                model.sources.first { it is SourceListState.Ready }
            } as SourceListState.Ready
            assertEquals(listOf(source), choices.sources)
            assertEquals(next, model.selectedPeriod)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            active.cancelAndJoin(); selected.cancelAndJoin(); sources.cancelAndJoin()
        }
    }

    @Test fun repositorySourcesAllocationsAndRelationshipsSurviveDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = "source-reopen-${java.util.UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, ManyManagerDatabase::class.java, file).build()
        try {
            fun repo(db: ManyManagerDatabase, calculator: CalculatorEntity) =
                ManyManagerRepository(db, CompletableDeferred(Result.success(calculator)), clock)
            var durable = repo(disk, disk.dao().initializeLocalCalculator())
            val first = success(durable.createSourceWithInitialAllocation("Same", period, 125))
            val second = success(durable.createSourceWithInitialAllocation("Same", period, 0))
            disk.dao().insertExpense(ExpenseEntity(owner, "relationship", 30, period.startEpochDay, null, first.id))
            success(durable.updateSourceAndAdjustAllocation(first.id, "Renamed", period, 25))
            disk.close()
            disk = Room.databaseBuilder(context, ManyManagerDatabase::class.java, file).build()
            durable = repo(disk, disk.dao().initializeLocalCalculator())
            val reopened = success(durable.snapshot(period))
            assertEquals(setOf(first.id, second.id), reopened.sources.map { it.source.id }.toSet())
            assertEquals("Renamed", reopened.sources.single { it.source.id == first.id }.source.name)
            assertEquals(150L, reopened.totalAllocatedMinor)
            assertEquals(first.id, reopened.expenses.single().sourceId)
            assertEquals(30L, reopened.totalSpentMinor)
            val next = success(durable.snapshot(BudgetPeriodCalculator(10, clock).next(period)))
            assertEquals(0L, next.totalAllocatedMinor)
            assertEquals(2, next.sources.size)
        } finally {
            disk.close()
            context.deleteDatabase(file)
        }
    }

    @Test fun positiveExpensesWithAndWithoutCategoryUseDatePeriodAndAllowOverspending() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 0))
        val category = success(repository.createCategory("Food"))
        val before = success(repository.createExpense(NewExpense(123, period.start.minusDays(1), source.id, category.id, "note")))
        val current = success(repository.createExpense(NewExpense(7, period.start, source.id)))
        val currentSnapshot = snapshot()
        assertEquals(listOf(current), currentSnapshot.expenses)
        assertEquals(7L, currentSnapshot.totalSpentMinor)
        assertEquals(-7L, currentSnapshot.remainingMinor)
        assertEquals(-7L, currentSnapshot.sources.single().remainingMinor)
        assertEquals(0L, currentSnapshot.totalAllocatedMinor)
        val previous = success(repository.snapshot(BudgetPeriodCalculator(10, clock).previous(period)))
        assertEquals(listOf(before), previous.expenses)
        assertEquals(123L, previous.categoryBreakdown.single().spentMinor)
    }

    @Test fun expenseValidationRejectsNonpositiveMissingAndForeignRelationships() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 0))
        assertEquals(DataError.NonPositiveAmount, error(repository.createExpense(NewExpense(0, period.start, source.id))))
        assertEquals(DataError.NonPositiveAmount, error(repository.createExpense(NewExpense(-1, period.start, source.id))))
        assertEquals(DataError.MissingEntity(EntityKind.SOURCE, "missing"),
            error(repository.createExpense(NewExpense(1, period.start, "missing"))))
        database.openHelper.writableDatabase.execSQL("INSERT INTO calculators (id, periodStartDay, currencyCode) VALUES ('foreign', 10, 'ILS')")
        database.dao().insertSource(BudgetSourceEntity("foreign", "foreign-source", "Cash"))
        database.dao().insertCategory(CategoryEntity("foreign", "foreign-category", "Food"))
        assertEquals(DataError.MissingEntity(EntityKind.SOURCE, "foreign-source"),
            error(repository.createExpense(NewExpense(1, period.start, "foreign-source"))))
        assertEquals(DataError.MissingEntity(EntityKind.CATEGORY, "foreign-category"),
            error(repository.createExpense(NewExpense(1, period.start, source.id, "foreign-category"))))
        database.dao().insertExpense(ExpenseEntity("foreign", "foreign-expense", 1, period.startEpochDay, null, "foreign-source"))
        assertEquals(DataError.MissingEntity(EntityKind.EXPENSE, "foreign-expense"),
            error(repository.correctExpenseAmount("foreign-expense", 2)))
        assertTrue(snapshot().expenses.isEmpty())
    }

    @Test fun correctionUpdatesOnlySameRecordAndObservationNeverDoubleCounts() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 1000))
        val category = success(repository.createCategory("Food"))
        val original = success(repository.createExpense(NewExpense(123, period.start, source.id, category.id, "keep")))
        val emissions = Channel<BudgetSnapshot>(Channel.UNLIMITED)
        val observer = launch { repository.observeSnapshot(period).collect { if (it is DataResult.Success) emissions.send(it.value) } }
        assertEquals(123L, withTimeout(10000) { emissions.receive() }.totalSpentMinor)
        val corrected = success(repository.correctExpenseAmount(original.id, 456))
        assertEquals(original.copy(amountMinor = 456), corrected)
        val updated = withTimeout(10000) {
            var value = emissions.receive()
            while (value.totalSpentMinor != 456L) value = emissions.receive()
            value
        }
        assertEquals(listOf(corrected), updated.expenses)
        assertEquals(1, updated.expenseCount)
        assertEquals(1000L, updated.totalAllocatedMinor)
        assertEquals(544L, updated.remainingMinor)
        observer.cancelAndJoin()
        assertEquals(DataError.NonPositiveAmount, error(repository.correctExpenseAmount(original.id, 0)))
        assertEquals(corrected, snapshot().expenses.single())
    }

    @Test fun expenseOverflowAndStorageFailuresRollbackCreationAndCorrection() = runBlocking {
        val source = success(repository.createSourceWithInitialAllocation("Cash", period, 0))
        val first = success(repository.createExpense(NewExpense(Long.MAX_VALUE - 1, period.start, source.id)))
        val second = success(repository.createExpense(NewExpense(1, period.start, source.id)))
        assertEquals(DataError.AmountOverflow, error(repository.createExpense(NewExpense(1, period.start, source.id))))
        assertEquals(DataError.AmountOverflow, error(repository.correctExpenseAmount(second.id, 2)))
        assertEquals(setOf(first, second), snapshot().expenses.toSet())
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_expense BEFORE UPDATE ON expenses BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        assertTrue(error(repository.correctExpenseAmount(second.id, 3)) is DataError.StorageFailure)
        assertEquals(second, snapshot().expenses.single { it.id == second.id })
        database.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_insert BEFORE INSERT ON expenses BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        assertTrue(error(repository.createExpense(NewExpense(1, period.start.minusDays(1), source.id))) is DataError.StorageFailure)
        assertEquals(2, snapshot().expenseCount)
    }

    @Test fun savedExpenseAndCorrectionSurviveIsolatedFileDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = "expense-reopen-${java.util.UUID.randomUUID()}.db"
        var disk = Room.databaseBuilder(context, ManyManagerDatabase::class.java, file).build()
        try {
            fun repo(db: ManyManagerDatabase) = ManyManagerRepository(db, initialization, clock)
            disk.dao().initializeLocalCalculator()
            val source = success(repo(disk).createSourceWithInitialAllocation("Cash", period, 0))
            val expense = success(repo(disk).createExpense(NewExpense(999, period.start, source.id, note = "durable")))
            success(repo(disk).correctExpenseAmount(expense.id, 1001))
            disk.close()
            disk = Room.databaseBuilder(context, ManyManagerDatabase::class.java, file).build()
            assertEquals(listOf(expense.copy(amountMinor = 1001)), success(repo(disk).snapshot(period)).expenses)
        } finally { disk.close(); context.deleteDatabase(file) }
    }

    @Test fun expenseOperationBundleNeverReplaysUnknownSubmission() = runBlocking {
        val draft = ExpenseDraft("1.23", period.start, "keep", null, "source")
        val restored = ExpenseCreationState.fromBundle(ExpenseCreationState(draft, submitting = true).toBundle())
        assertEquals(draft, restored.draft)
        assertTrue(restored.interrupted)
        val store = ViewModelStore()
        try {
            withContext(Dispatchers.Main) {
                val vm = ViewModelProvider(store, PersistentDataViewModelFactory(repository))[PersistentDataViewModel::class.java]
                vm.restoreExpenseCreation(restored)
                assertNull(vm.submitExpenseCreation())
                assertFalse(vm.canSaveExpense())
                vm.updateExpenseDraft(draft.copy(amount = "9"))
                assertEquals(draft, vm.expenseCreationState.value!!.draft)
            }
        } finally { withContext(Dispatchers.Main) { store.clear() } }
        assertTrue(snapshot().expenses.isEmpty())
    }

    @Test fun breakdownsFollowCommittedCorrectionsRenamesAndBudgetAdjustments() = runBlocking {
        val a = success(repository.createSourceWithInitialAllocation("Same", period, 500))
        val b = success(repository.createSourceWithInitialAllocation("Same", period, 0))
        val catA = success(repository.createCategory("Same"))
        val catB = success(repository.createCategory("Same"))
        success(repository.createCategory("Unused"))
        val first = success(repository.createExpense(NewExpense(29, period.start, a.id, catA.id)))
        success(repository.createExpense(NewExpense(11, period.start, b.id, catB.id)))
        success(repository.createExpense(NewExpense(18, period.start, b.id)))
        fun verify(value: BudgetSnapshot, spent: Long) {
            assertEquals(spent, value.totalSpentMinor)
            assertEquals(spent, BudgetCalculations.sum(value.categoryBreakdown.map { it.spentMinor }))
            assertEquals(spent, BudgetCalculations.sum(value.sourceBreakdown.map { it.spentMinor }))
            assertEquals(3, value.expenseCount)
            assertEquals(3, value.categoryBreakdown.sumOf { it.expenseCount })
            assertEquals(3, value.sourceBreakdown.sumOf { it.expenseCount })
        }
        verify(snapshot(), 58)
        assertEquals(3, snapshot().categoryBreakdown.size)
        assertEquals(2, snapshot().sourceBreakdown.size)
        assertEquals(29L, snapshot().sourceBreakdown.single { it.source.id == b.id }.spentMinor)
        success(repository.correctExpenseAmount(first.id, 30))
        verify(snapshot(), 59)
        val before = snapshot()
        success(repository.renameSource(b.id, "Renamed"))
        val renamed = snapshot()
        verify(renamed, 59)
        assertEquals(before.categoryBreakdown, renamed.categoryBreakdown)
        assertEquals("Renamed", renamed.sourceBreakdown.single { it.source.id == b.id }.source.name)
        assertEquals(before.sources.map { it.allocatedMinor }, renamed.sources.map { it.allocatedMinor })
        success(repository.updateSourceAndAdjustAllocation(b.id, "Renamed", period, 100))
        val adjusted = snapshot()
        verify(adjusted, 59)
        assertEquals(600L, adjusted.totalAllocatedMinor)
        assertEquals(541L, adjusted.remainingMinor)
        assertEquals(before.categoryBreakdown, adjusted.categoryBreakdown)
        assertTrue(success(repository.snapshot(BudgetPeriodCalculator(10, clock).next(period))).categoryBreakdown.isEmpty())
    }

    private suspend fun fixtureExpense(
        id: String, amount: Long, date: LocalDate, sourceId: String, categoryId: String? = null
    ) {
        database.dao().insertExpense(ExpenseEntity(owner, id, amount, date.toEpochDay(),
            categoryId, sourceId))
    }

    private suspend fun snapshot() = success(repository.snapshot(period))
    private fun <T> success(result: DataResult<T>): T {
        assertTrue("Expected success, got $result", result is DataResult.Success)
        return (result as DataResult.Success).value
    }
    private fun error(result: DataResult<*>): DataError {
        assertTrue("Expected failure, got $result", result is DataResult.Failure)
        return (result as DataResult.Failure).error
    }
}
