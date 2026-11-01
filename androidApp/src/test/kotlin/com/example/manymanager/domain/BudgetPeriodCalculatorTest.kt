package com.example.manymanager.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class BudgetPeriodCalculatorTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC)
    private val calculator = BudgetPeriodCalculator(10, clock)

    @Test fun dayBeforeBoundaryBelongsToPreviousPeriod() {
        assertPeriod("2026-10-10", "2026-11-10", calculator.periodContaining(date("2026-11-09")))
    }

    @Test fun boundaryDayStartsNewPeriod() {
        assertPeriod("2026-11-10", "2026-12-10", calculator.periodContaining(date("2026-11-10")))
    }

    @Test fun novemberPeriodIncludesDecemberNinthAndExcludesTenth() {
        val period = calculator.periodContaining(date("2026-11-10"))
        assertEquals(period, calculator.periodContaining(date("2026-12-09")))
        assertTrue(date("2026-12-09") in period)
        assertFalse(date("2026-12-10") in period)
        assertEquals(calculator.next(period), calculator.periodContaining(date("2026-12-10")))
    }

    @Test fun decemberJanuaryTransitionPreservesYearBoundaries() {
        val december = calculator.periodContaining(date("2027-01-09"))
        assertPeriod("2026-12-10", "2027-01-10", december)
        assertPeriod("2027-01-10", "2027-02-10", calculator.next(december))
        assertPeriod("2026-11-10", "2026-12-10", calculator.previous(december))
    }

    @Test fun leapFebruaryUsesCalendarArithmetic() {
        val february = calculator.periodContaining(date("2024-02-29"))
        assertPeriod("2024-02-10", "2024-03-10", february)
        assertEquals(february, calculator.periodContaining(date("2024-03-09")))
        assertPeriod("2024-01-10", "2024-02-10",
            calculator.periodContaining(date("2024-02-09")))
    }

    @Test fun nonLeapFebruaryUsesCalendarArithmetic() {
        assertPeriod("2025-02-10", "2025-03-10",
            calculator.periodContaining(date("2025-02-28")))
        assertPeriod("2025-03-10", "2025-04-10",
            calculator.periodContaining(date("2025-03-10")))
    }

    @Test fun explicitDatesAreIndependentOfTheClocksYear() {
        assertPeriod("1999-12-10", "2000-01-10",
            calculator.periodContaining(date("2000-01-01")))
        assertPeriod("2040-02-10", "2040-03-10",
            calculator.periodContaining(date("2040-02-29")))
    }

    @Test fun navigationRoundTripsAndAdjacentPeriodsHaveNoGapOrOverlap() {
        listOf(1, 10, 28).forEach { day ->
            val configured = BudgetPeriodCalculator(day, clock)
            listOf("2024-02-29", "2025-02-28", "2026-12-31").forEach { value ->
                val period = configured.periodContaining(date(value))
                val previous = configured.previous(period)
                val next = configured.next(period)
                assertEquals(period, configured.previous(next))
                assertEquals(period, configured.next(previous))
                assertEquals(previous.endExclusive, period.start)
                assertEquals(period.endExclusive, next.start)
                assertTrue(period.endExclusive.minusDays(1) in period)
                assertFalse(period.endExclusive in period)
                assertTrue(period.endExclusive in next)
                assertFalse(period.start in previous)
                assertTrue(period.start in period)
            }
        }
    }

    @Test fun dayOneUsesCalendarMonthBoundaries() {
        val configured = BudgetPeriodCalculator(1, clock)
        assertPeriod("2024-02-01", "2024-03-01",
            configured.periodContaining(date("2024-02-29")))
        assertPeriod("2026-12-01", "2027-01-01",
            configured.periodContaining(date("2026-12-01")))
    }

    @Test fun dayTwentyEightWorksAcrossBothKindsOfFebruaryWithoutClamping() {
        val configured = BudgetPeriodCalculator(28, clock)
        assertPeriod("2025-01-28", "2025-02-28",
            configured.periodContaining(date("2025-02-27")))
        assertPeriod("2025-02-28", "2025-03-28",
            configured.periodContaining(date("2025-02-28")))
        assertPeriod("2024-02-28", "2024-03-28",
            configured.periodContaining(date("2024-02-29")))
    }

    @Test fun everySupportedDayClassifiesTheDayBeforeAndTheBoundary() {
        (1..28).forEach { day ->
            val configured = BudgetPeriodCalculator(day, clock)
            val boundary = LocalDate.of(2026, 11, day)
            assertEquals(boundary, configured.periodContaining(boundary).start)
            assertEquals(boundary,
                configured.periodContaining(boundary.minusDays(1)).endExclusive)
        }
    }

    @Test fun rejectsUnsupportedStartDaysRatherThanClamping() {
        listOf(Int.MIN_VALUE, -1, 0, 29, 30, 31, 32, Int.MAX_VALUE).forEach { day ->
            assertThrows(IllegalArgumentException::class.java) {
                BudgetPeriodCalculator(day, clock)
            }
        }
    }

    @Test fun navigationRejectsIncompatibleStartOrEnd() {
        listOf(
            BudgetPeriod(date("2026-11-01"), date("2026-12-01")),
            BudgetPeriod(date("2026-11-10"), date("2026-12-09")),
            BudgetPeriod(date("2026-11-10"), date("2026-12-11")),
            BudgetPeriod(date("2026-11-10"), date("2027-01-10")),
        ).forEach { incompatible ->
            assertThrows(IllegalArgumentException::class.java) { calculator.previous(incompatible) }
            assertThrows(IllegalArgumentException::class.java) { calculator.next(incompatible) }
        }
    }

    @Test fun currentPeriodUsesTheSuppliedFixedClock() {
        assertPeriod("2026-11-10", "2026-12-10", calculator.currentPeriod())
    }

    @Test fun sameInstantInDifferentClockZonesCanBelongToDifferentPeriods() {
        val instant = Instant.parse("2026-11-10T00:30:00Z")
        val west = Clock.fixed(instant, ZoneId.of("America/Los_Angeles"))
        val east = Clock.fixed(instant, ZoneId.of("Asia/Jerusalem"))
        assertPeriod("2026-10-10", "2026-11-10",
            BudgetPeriodCalculator(10, west).currentPeriod())
        assertPeriod("2026-11-10", "2026-12-10",
            BudgetPeriodCalculator(10, east).currentPeriod())
    }

    @Test fun currentPeriodReadsClockAgainOnEachCall() {
        val advancing = object : Clock() {
            var instantValue: Instant = Instant.parse("2026-11-09T23:59:59Z")
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(instantValue, zone)
            override fun instant(): Instant = instantValue
        }
        val configured = BudgetPeriodCalculator(10, advancing)
        assertPeriod("2026-10-10", "2026-11-10", configured.currentPeriod())
        advancing.instantValue = Instant.parse("2026-11-10T00:00:00Z")
        assertPeriod("2026-11-10", "2026-12-10", configured.currentPeriod())
    }

    private fun date(value: String): LocalDate = LocalDate.parse(value)

    private fun assertPeriod(start: String, end: String, actual: BudgetPeriod) {
        assertEquals(BudgetPeriod(date(start), date(end)), actual)
    }
}
