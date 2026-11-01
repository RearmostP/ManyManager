package com.example.manymanager.domain

import java.time.LocalDate

data class Calculator(val id: String, val periodStartDay: Int, val currencyCode: String)
data class Category(val calculatorId: String, val id: String, val name: String)
data class BudgetSource(val calculatorId: String, val id: String, val name: String)
data class Expense(
    val calculatorId: String, val id: String, val amountMinor: Long, val date: LocalDate,
    val categoryId: String?, val sourceId: String, val note: String?,
)
data class SourceBudget(
    val source: BudgetSource, val allocatedMinor: Long, val spentMinor: Long,
    val remainingMinor: Long, val expenseCount: Int,
)
data class CategorySummary(val categoryId: String?, val spentMinor: Long, val expenseCount: Int)
data class BudgetSnapshot(
    val calculator: Calculator,
    val period: BudgetPeriod,
    val categories: List<Category>,
    val sources: List<SourceBudget>,
    val expenses: List<Expense>,
    val totalAllocatedMinor: Long,
    val totalSpentMinor: Long,
    val remainingMinor: Long,
    val expenseCount: Int,
    val categoryBreakdown: List<CategorySummary>,
) {
    /** Spending rows only; allocation/remaining rows retain their separate management order. */
    val sourceBreakdown: List<SourceBudget> = sources.filter { it.expenseCount > 0 }
        .sortedWith(compareByDescending<SourceBudget> { it.spentMinor }.thenBy { it.source.id })
}
data class BudgetAdjustmentPreview(
    val sourceId: String, val period: BudgetPeriod, val currentAllocatedMinor: Long,
    val deltaMinor: Long, val resultingAllocatedMinor: Long,
)

data class NewExpense(
    val amountMinor: Long, val date: LocalDate, val sourceId: String,
    val categoryId: String? = null, val note: String? = null,
)

enum class EntityKind { CALCULATOR, CATEGORY, SOURCE, EXPENSE }

sealed interface DataError {
    data object InvalidAmount : DataError
    data object NonPositiveAmount : DataError
    data object InvalidDate : DataError
    data object BlankName : DataError
    data class MissingEntity(val kind: EntityKind, val id: String) : DataError
    data object InvalidPeriod : DataError
    data object NegativeAllocatedBudget : DataError
    data object AmountOverflow : DataError
    data class InitializationFailure(val cause: Throwable) : DataError
    data class StorageFailure(val cause: Throwable) : DataError
}

sealed interface DataResult<out T> {
    data class Success<T>(val value: T) : DataResult<T>
    data class Failure(val error: DataError) : DataResult<Nothing>
}
