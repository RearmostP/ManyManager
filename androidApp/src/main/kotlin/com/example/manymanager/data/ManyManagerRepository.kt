package com.example.manymanager.data

import androidx.room.withTransaction
import com.example.manymanager.data.local.*
import com.example.manymanager.domain.*
import java.time.Clock
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Persistent pipeline scoped to the initialized local calculator, consumed through the ViewModel.
 * Each operation reloads saved settings; no temporary data or permanently cached active period.
 */
class ManyManagerRepository(
    private val database: ManyManagerDatabase,
    private val initialization: Deferred<Result<CalculatorEntity>>,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dao get() = database.dao()

    suspend fun currentPeriod(): DataResult<BudgetPeriod> =
        execute { resolvePeriod(it, null) }

    suspend fun periodContaining(date: LocalDate): DataResult<BudgetPeriod> =
        execute { configuredCalculator(it).periodContaining(date) }

    fun observeCategories(): Flow<DataResult<List<Category>>> =
        observe(arrayOf("calculators", "categories")) {
            execute { calculator -> dao.categories(calculator.id).map { it.domain() } }
        }

    fun observeSources(): Flow<DataResult<List<BudgetSource>>> =
        observe(arrayOf("calculators", "budget_sources")) {
            execute { calculator -> dao.sources(calculator.id).map { it.domain() } }
        }

    /**
     * Null selects the period containing today on each transactional read.
     * Callers restart/refresh on resume or civil-date changes; no background scheduler is installed.
     */
    fun observeSnapshot(period: BudgetPeriod? = null): Flow<DataResult<BudgetSnapshot>> =
        observe(arrayOf("calculators", "categories", "budget_sources", "budget_allocations", "expenses")) {
            snapshot(period)
        }

    suspend fun snapshot(period: BudgetPeriod? = null): DataResult<BudgetSnapshot> =
        execute { calculator ->
            val selected = resolvePeriod(calculator, period)
            val allocations = dao.allocations(calculator.id, selected.startEpochDay)
            if (allocations.any { it.allocatedMinor < 0 }) reject(DataError.NegativeAllocatedBudget)
            BudgetCalculations.snapshot(
                calculator.domain(), selected,
                dao.categories(calculator.id).map { it.domain() },
                dao.sources(calculator.id).map { it.domain() },
                allocations.associate { it.sourceId to it.allocatedMinor },
                dao.expenses(calculator.id, selected.startEpochDay, selected.endExclusiveEpochDay)
                    .map { it.domain() },
            )
        }

    suspend fun createCategory(name: String): DataResult<Category> = execute { calculator ->
        val category = CategoryEntity(calculator.id, UUID.randomUUID().toString(), cleanName(name))
        dao.insertCategory(category)
        category.domain()
    }

    suspend fun createSourceWithInitialAllocation(
        name: String, period: BudgetPeriod, initialMinor: Long
    ): DataResult<BudgetSource> = execute { calculator ->
        val clean = cleanName(name)
        val selected = resolvePeriod(calculator, period)
        if (initialMinor < 0) reject(DataError.NegativeAllocatedBudget)
        val source = BudgetSourceEntity(calculator.id, UUID.randomUUID().toString(), clean)
        validateAllocationTotal(calculator.id, selected, source.id, initialMinor)
        dao.insertSource(source)
        dao.insertAllocation(BudgetAllocationEntity(
            calculator.id, source.id, selected.startEpochDay, initialMinor
        ))
        source.domain()
    }

    suspend fun renameSource(sourceId: String, name: String): DataResult<BudgetSource> =
        execute { calculator ->
            val clean = cleanName(name)
            val source = requireSource(calculator.id, sourceId)
            check(dao.renameSource(calculator.id, sourceId, clean) == 1)
            source.copy(name = clean).domain()
        }

    /** A signed preview may be negative, as in the existing editor; preview never writes. */
    suspend fun previewBudgetAdjustment(
        sourceId: String, period: BudgetPeriod, deltaMinor: Long
    ): DataResult<BudgetAdjustmentPreview> = execute { calculator ->
        val selected = resolvePeriod(calculator, period)
        requireSource(calculator.id, sourceId)
        val current = dao.allocation(calculator.id, sourceId, selected.startEpochDay)?.allocatedMinor ?: 0L
        val result = Math.addExact(current, deltaMinor)
        validateAllocationTotal(calculator.id, selected, sourceId, result)
        BudgetAdjustmentPreview(sourceId, selected, current, deltaMinor, result)
    }

    /** Read the latest allocation and commit name + adjustment together, never using a stale preview. */
    suspend fun updateSourceAndAdjustAllocation(
        sourceId: String, name: String, period: BudgetPeriod, deltaMinor: Long
    ): DataResult<BudgetAdjustmentPreview> = execute { calculator ->
        val clean = cleanName(name)
        val selected = resolvePeriod(calculator, period)
        requireSource(calculator.id, sourceId)
        val current = dao.allocation(calculator.id, sourceId, selected.startEpochDay)?.allocatedMinor ?: 0L
        val result = Math.addExact(current, deltaMinor)
        if (result < 0) reject(DataError.NegativeAllocatedBudget)
        validateAllocationTotal(calculator.id, selected, sourceId, result)
        check(dao.renameSource(calculator.id, sourceId, clean) == 1)
        dao.putAllocation(BudgetAllocationEntity(calculator.id, sourceId, selected.startEpochDay, result))
        BudgetAdjustmentPreview(sourceId, selected, current, deltaMinor, result)
    }

    private suspend fun validateAllocationTotal(
        calculatorId: String, period: BudgetPeriod, sourceId: String, replacement: Long
    ) {
        val others = dao.allocations(calculatorId, period.startEpochDay)
            .filter { it.sourceId != sourceId }.map { it.allocatedMinor }
        BudgetCalculations.sum(others + replacement)
    }

    private suspend fun requireSource(calculatorId: String, sourceId: String): BudgetSourceEntity =
        dao.source(calculatorId, sourceId)
            ?: reject(DataError.MissingEntity(EntityKind.SOURCE, sourceId))

    private fun cleanName(name: String): String =
        name.trim().also { if (it.isEmpty()) reject(DataError.BlankName) }

    private fun configuredCalculator(calculator: CalculatorEntity): BudgetPeriodCalculator =
        try {
            BudgetPeriodCalculator(calculator.periodStartDay, clock)
        } catch (_: IllegalArgumentException) {
            reject(DataError.InvalidPeriod)
        }

    private fun resolvePeriod(calculator: CalculatorEntity, period: BudgetPeriod?): BudgetPeriod {
        val configured = configuredCalculator(calculator)
        val canonical = if (period == null) configured.currentPeriod()
            else configured.periodContaining(period.start)
        if (period != null && period != canonical) reject(DataError.InvalidPeriod)
        return canonical
    }

    private suspend fun initializedCalculatorId(): String {
        try {
            return initialization.await().getOrElse { cause ->
                if (cause is CancellationException) throw cause
                reject(DataError.InitializationFailure(cause))
            }.id
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (rejected: Rejected) {
            throw rejected
        } catch (failure: Exception) {
            reject(DataError.InitializationFailure(failure))
        }
    }

    private suspend fun <T> execute(block: suspend (CalculatorEntity) -> T): DataResult<T> =
        withContext(dispatcher) {
            try {
                val id = initializedCalculatorId()
                DataResult.Success(database.withTransaction {
                    val calculator = dao.calculator(id)
                        ?: reject(DataError.MissingEntity(EntityKind.CALCULATOR, id))
                    block(calculator)
                })
            } catch (failure: Exception) {
                DataResult.Failure(errorFor(failure))
            }
        }

    private fun <T> observe(
        tables: Array<String>, read: suspend () -> DataResult<T>
    ): Flow<DataResult<T>> = flow {
        initializedCalculatorId()
        // Register before the initial read. Every invalidation triggers a whole DB transaction;
        // no combine of independent queries can emit mixed revisions.
        database.invalidationTracker.createFlow(*tables, emitInitialState = true).collect {
            emit(read())
        }
    }.catch { failure -> emit(DataResult.Failure(errorFor(failure))) }
        .flowOn(dispatcher).distinctUntilChanged()

    private fun errorFor(failure: Throwable): DataError = when (failure) {
        is CancellationException -> throw failure
        is Rejected -> failure.error
        is ArithmeticException -> DataError.AmountOverflow
        is DateTimeException -> DataError.InvalidPeriod
        else -> DataError.StorageFailure(failure)
    }

    private class Rejected(val error: DataError) : RuntimeException()
    private fun reject(error: DataError): Nothing = throw Rejected(error)

    private fun CalculatorEntity.domain() = Calculator(id, periodStartDay, currencyCode)
    private fun CategoryEntity.domain() = Category(calculatorId, id, name)
    private fun BudgetSourceEntity.domain() = BudgetSource(calculatorId, id, name)
    private fun ExpenseEntity.domain() = Expense(
        calculatorId, id, amountMinor, LocalDate.ofEpochDay(dateEpochDay), categoryId, sourceId, note
    )
}
