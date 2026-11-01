package com.example.manymanager.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class BudgetPeriodTest {
    private val start = LocalDate.of(2026, 11, 10)
    private val end = LocalDate.of(2026, 12, 10)

    @Test fun containsStartAndFinalDayButExcludesOutsideDates() {
        val period = BudgetPeriod(start, end)
        assertFalse(start.minusDays(1) in period)
        assertTrue(start in period)
        assertTrue(end.minusDays(1) in period)
        assertFalse(end in period)
    }

    @Test fun epochDaysAndIdentityAreDerivedFromCivilBoundaries() {
        val period = BudgetPeriod(start, end)
        assertEquals(start.toEpochDay(), period.startEpochDay)
        assertEquals(end.toEpochDay(), period.endExclusiveEpochDay)
        assertEquals(period.startEpochDay, period.id)
        val shifted = period.copy(start = start.plusMonths(1), endExclusive = end.plusMonths(1))
        assertEquals(shifted.start.toEpochDay(), shifted.id)
        assertNotEquals(period.id, shifted.id)
    }

    @Test fun rejectsEmptyOrReversedIntervalsIncludingCopy() {
        assertThrows(IllegalArgumentException::class.java) { BudgetPeriod(start, start) }
        assertThrows(IllegalArgumentException::class.java) { BudgetPeriod(end, start) }
        assertThrows(IllegalArgumentException::class.java) {
            BudgetPeriod(start, end).copy(endExclusive = start)
        }
    }
}
