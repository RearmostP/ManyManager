package com.example.manymanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Storage primitives only. Business operations and screen observation belong to later stages. */
@Dao
abstract class ManyManagerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCalculatorIfAbsent(calculator: CalculatorEntity): Long

    @Query("SELECT * FROM calculators WHERE id = :id")
    abstract suspend fun calculator(id: String): CalculatorEntity?

    @Transaction
    open suspend fun initializeLocalCalculator(): CalculatorEntity {
        insertCalculatorIfAbsent(CalculatorEntity(LOCAL_CALCULATOR_ID))
        return checkNotNull(calculator(LOCAL_CALCULATOR_ID))
    }

    // ABORT is intentional: these are inserts, never silent replacement of existing facts.
    @Insert abstract suspend fun insertCategory(category: CategoryEntity)
    @Insert abstract suspend fun insertSource(source: BudgetSourceEntity)
    @Insert abstract suspend fun insertAllocation(allocation: BudgetAllocationEntity)
    @Insert abstract suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE calculatorId = :calculatorId AND id = :id")
    abstract suspend fun expense(calculatorId: String, id: String): ExpenseEntity?

    @Query("""
        SELECT * FROM budget_allocations
        WHERE calculatorId = :calculatorId AND sourceId = :sourceId
            AND periodStartEpochDay = :periodStartEpochDay
    """)
    abstract suspend fun allocation(
        calculatorId: String, sourceId: String, periodStartEpochDay: Long
    ): BudgetAllocationEntity?

    companion object {
        /** Internal identity of the initial local calculator; never derived from a translated label. */
        const val LOCAL_CALCULATOR_ID = "local-calculator"
    }
}
