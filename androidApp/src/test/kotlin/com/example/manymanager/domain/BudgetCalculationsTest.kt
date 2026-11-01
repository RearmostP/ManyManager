package com.example.manymanager.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BudgetCalculationsTest {
    private val calculator = Calculator("local", 10, "ILS")
    private val period = BudgetPeriod(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 10))
    private val sources = listOf(BudgetSource("local", "a", "Same"), BudgetSource("local", "b", "Same"))
    private fun expense(id: String, amount: Long, source: String = "a", category: String? = null) =
        Expense("local", id, amount, period.start, category, source, null)

    @Test fun exactSumsDoNotOverflowIntermediateValues() {
        assertEquals(Long.MAX_VALUE, BudgetCalculations.sum(listOf(Long.MAX_VALUE, 1L, -1L)))
        assertEquals(Long.MIN_VALUE, BudgetCalculations.sum(listOf(Long.MIN_VALUE, -1L, 1L)))
        val snapshot = snapshot(listOf(expense("1", Long.MAX_VALUE), expense("2", 1), expense("3", -1)))
        assertEquals(Long.MAX_VALUE, snapshot.totalSpentMinor)
    }

    @Test fun groupsByStableIdsAndNullCategoryWithoutDoubleCounting() {
        val snapshot = snapshot(listOf(
            expense("1", 30, category = "cat"), expense("2", 20, source = "b"),
            expense("3", 10, source = "b"),
        ), mapOf("a" to 100, "b" to 0))
        assertEquals(60L, snapshot.totalSpentMinor)
        assertEquals(100L, snapshot.totalAllocatedMinor)
        assertEquals(40L, snapshot.remainingMinor)
        assertEquals(3, snapshot.expenseCount)
        assertEquals(70L, snapshot.sources[0].remainingMinor)
        assertEquals(-30L, snapshot.sources[1].remainingMinor)
        assertEquals(CategorySummary(null, 30L, 2),
            snapshot.categoryBreakdown.single { it.categoryId == null })
        assertEquals(CategorySummary("cat", 30L, 1),
            snapshot.categoryBreakdown.single { it.categoryId == "cat" })
    }

    @Test fun missingAllocationIsZeroAndRemainingMayBeNegative() {
        val snapshot = snapshot(listOf(expense("1", 500)))
        assertEquals(2, snapshot.sources.size)
        assertEquals(0L, snapshot.totalAllocatedMinor)
        assertEquals(-500L, snapshot.remainingMinor)
        assertEquals(0L, snapshot.sources[1].remainingMinor)
    }

    @Test fun finalSumOverflowIsExplicit() {
        assertThrows(ArithmeticException::class.java) {
            BudgetCalculations.sum(listOf(Long.MAX_VALUE, 1L))
        }
        assertThrows(ArithmeticException::class.java) {
            BudgetCalculations.sum(listOf(Long.MIN_VALUE, -1L))
        }
    }

    @Test fun categoryOrSourceOverflowIsNotHiddenByOtherGroups() {
        assertThrows(ArithmeticException::class.java) {
            snapshot(listOf(expense("1", Long.MAX_VALUE), expense("2", 1),
                expense("3", -1, source = "b")))
        }
        assertThrows(ArithmeticException::class.java) {
            snapshot(listOf(expense("1", Long.MAX_VALUE, category = "cat"),
                expense("2", 1, source = "b", category = "cat"),
                expense("3", -1, source = "b")))
        }
    }

    @Test fun remainingOverflowIsExplicitWithoutImposingAnExpenseSignPolicy() {
        assertThrows(ArithmeticException::class.java) {
            snapshot(listOf(expense("1", Long.MIN_VALUE)))
        }
    }

    @Test fun breakdownsHaveStableSpendingOrderAndEachIndependentlyMatchesOverallFacts() {
        val categories = listOf(Category("local", "a", "Same"), Category("local", "b", "Same"),
            Category("local", "unused", "Unused"))
        val expenses = listOf(expense("1", 29, "b", "b"), expense("2", 11, "a", "a"),
            expense("3", 18, "a", "a"), expense("4", 29, "b"))
        val result = BudgetCalculations.snapshot(calculator, period, categories, sources,
            emptyMap(), expenses)
        assertEquals(listOf(null, "a", "b"), result.categoryBreakdown.map { it.categoryId })
        assertEquals(listOf("b", "a"), result.sourceBreakdown.map { it.source.id })
        assertEquals(result.totalSpentMinor, BudgetCalculations.sum(result.categoryBreakdown.map { it.spentMinor }))
        assertEquals(result.totalSpentMinor, BudgetCalculations.sum(result.sourceBreakdown.map { it.spentMinor }))
        assertEquals(result.expenseCount, result.categoryBreakdown.sumOf { it.expenseCount })
        assertEquals(result.expenseCount, result.sourceBreakdown.sumOf { it.expenseCount })
        assertEquals(0L, result.sourceBreakdown.first().allocatedMinor)
        val reversed = BudgetCalculations.snapshot(calculator, period, categories.reversed(),
            sources.reversed(), emptyMap(), expenses.reversed())
        assertEquals(result.categoryBreakdown, reversed.categoryBreakdown)
        assertEquals(result.sourceBreakdown, reversed.sourceBreakdown)
        val empty = snapshot(emptyList())
        assertTrue(empty.categoryBreakdown.isEmpty())
        assertTrue(empty.sourceBreakdown.isEmpty())
    }

    private fun snapshot(expenses: List<Expense>, allocations: Map<String, Long> = emptyMap()) =
        BudgetCalculations.snapshot(calculator, period, emptyList(), sources, allocations, expenses)
}
