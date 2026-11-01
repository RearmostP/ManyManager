package com.example.manymanager.domain

import java.math.BigInteger

/** Exact aggregation: only final exposed values must fit Long, never intermediate sums. */
object BudgetCalculations {
    fun sum(values: Iterable<Long>): Long = exactSum(values).checkedLong()

    fun snapshot(
        calculator: Calculator,
        period: BudgetPeriod,
        categories: List<Category>,
        sources: List<BudgetSource>,
        allocations: Map<String, Long>,
        expenses: List<Expense>,
    ): BudgetSnapshot {
        val bySource = expenses.groupBy { it.sourceId }
        val sourceBudgets = sources.map { source ->
            val rows = bySource[source.id].orEmpty()
            val allocated = allocations[source.id] ?: 0L
            val spent = exactSum(rows.map { it.amountMinor })
            SourceBudget(source, allocated, spent.checkedLong(),
                BigInteger.valueOf(allocated).subtract(spent).checkedLong(), rows.size)
        }
        val allocated = exactSum(allocations.values)
        val spent = exactSum(expenses.map { it.amountMinor })
        val breakdown = expenses.groupBy { it.categoryId }.map { (id, rows) ->
            CategorySummary(id, sum(rows.map { it.amountMinor }), rows.size)
        }
        return BudgetSnapshot(calculator, period, categories.toList(), sourceBudgets,
            expenses.toList(), allocated.checkedLong(), spent.checkedLong(),
            allocated.subtract(spent).checkedLong(), expenses.size, breakdown)
    }

    // BigInteger.longValueExact() requires API 31; the app also supports API 30.
    private val minimum = BigInteger.valueOf(Long.MIN_VALUE)
    private val maximum = BigInteger.valueOf(Long.MAX_VALUE)

    private fun BigInteger.checkedLong(): Long {
        if (this < minimum || this > maximum) throw ArithmeticException("Amount does not fit Long")
        return toLong()
    }

    private fun exactSum(values: Iterable<Long>): BigInteger =
        values.fold(BigInteger.ZERO) { total, value -> total.add(BigInteger.valueOf(value)) }
}
