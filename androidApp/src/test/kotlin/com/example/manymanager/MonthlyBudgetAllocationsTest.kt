package com.example.manymanager

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class MonthlyBudgetAllocationsTest {
    // Fixtures stay in tests; the real source list remains empty.
    private val sources = listOf(PaymentSource("source-a", "Same name"), PaymentSource("source-b", "Same name"))
    private val month = YearMonth.of(2026, 12)

    @Test fun emptySourcesAndUnallocatedMonthsHaveZeroTotal() {
        assertEquals(0L, MonthlyBudgetAllocations(emptyList()).total(month))
        assertEquals(0L, MonthlyBudgetAllocations(sources).total(month))
    }

    @Test fun totalsAreExactAndUseStableSourceIds() {
        val budgets = MonthlyBudgetAllocations(sources)
        assertTrue(budgets.apply(month, "source-a", 10))
        assertTrue(budgets.apply(month, "source-b", 20))
        assertEquals(30L, budgets.total(month))
        assertEquals(10L, budgets.amount(month, "source-a"))
        assertEquals(20L, budgets.amount(month, "source-b"))
    }

    @Test fun replacesAllocationsAndAcceptsZero() {
        val budgets = MonthlyBudgetAllocations(sources)
        budgets.apply(month, "source-a", 12345)
        budgets.apply(month, "source-a", 50)
        assertEquals(50L, budgets.total(month))
        assertTrue(budgets.apply(month, "source-a", 0))
        assertEquals(0L, budgets.total(month))
    }

    @Test fun monthsAndYearsRemainIndependentWithoutRollover() {
        val budgets = MonthlyBudgetAllocations(sources)
        budgets.apply(month, "source-a", 123)
        assertEquals(0L, budgets.total(month.plusMonths(1)))
        assertEquals(0L, budgets.total(month.plusYears(1)))
        budgets.apply(month.plusMonths(1), "source-a", 456)
        budgets.apply(month.plusYears(1), "source-a", 789)
        assertEquals(123L, budgets.total(month))
        assertEquals(456L, budgets.total(month.plusMonths(1)))
        assertEquals(789L, budgets.total(month.plusYears(1)))
    }

    @Test fun rejectsUnknownSourcesAndNegativeAmounts() {
        val budgets = MonthlyBudgetAllocations(sources)
        assertFalse(budgets.apply(month, "Same name", 123))
        assertFalse(budgets.apply(month, "source-a", -1))
        assertEquals(0L, budgets.total(month))
    }

    @Test fun rejectsTotalOverflowAtomically() {
        val budgets = MonthlyBudgetAllocations(sources)
        assertTrue(budgets.apply(month, "source-a", Long.MAX_VALUE))
        assertFalse(budgets.apply(month, "source-b", 1))
        assertEquals(Long.MAX_VALUE, budgets.total(month))
        assertEquals(0L, budgets.amount(month, "source-b"))
    }

    @Test fun sourceIdentityAndMonthlyAdjustmentsStayTogether() {
        val budgets = MonthlyBudgetAllocations(emptyList())
        val source = budgets.addSource("Card", month, 50_000)!!
        assertEquals(50_000L, budgets.amount(month, source.id))
        assertEquals(0L, budgets.amount(month.plusMonths(1), source.id))
        assertEquals(40_000L, budgets.previewAdjustment(month, source.id, -10_000))
        assertEquals(50_000L, budgets.amount(month, source.id)) // preview and cancel do not write
        assertTrue(budgets.adjust(month, source.id, -10_000))
        assertTrue(budgets.renameSource(source.id, "Main card"))
        assertEquals("Main card", budgets.sources.single().name)
        assertEquals(40_000L, budgets.amount(month, source.id))
        assertEquals(0L, budgets.amount(month.plusMonths(1), source.id))
    }

    @Test fun signedPreviewDoesNotOverrideExistingNegativeAllocationPolicy() {
        val budgets = MonthlyBudgetAllocations(emptyList())
        val source = budgets.addSource("Cash", month, 100)!!
        assertEquals(-100L, budgets.previewAdjustment(month, source.id, -200))
        assertFalse(budgets.adjust(month, source.id, -200))
        assertEquals(100L, budgets.amount(month, source.id))
        assertNull(budgets.addSource(" ", month, 100))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateSourceIds() {
        MonthlyBudgetAllocations(listOf(PaymentSource("same", "One"), PaymentSource("same", "Two")))
    }
}
