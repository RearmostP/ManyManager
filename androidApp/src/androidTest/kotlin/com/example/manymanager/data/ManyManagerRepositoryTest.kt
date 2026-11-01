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
        val draft = ExpenseDraft("150.00", "10/11/2026", "Groceries", "cat-123", "src-456")
        val bundle = draft.toBundle()
        val restoredDraft = ExpenseDraft.fromBundle(bundle)
        assertEquals("150.00", restoredDraft.amount)
        assertEquals("10/11/2026", restoredDraft.date)
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
