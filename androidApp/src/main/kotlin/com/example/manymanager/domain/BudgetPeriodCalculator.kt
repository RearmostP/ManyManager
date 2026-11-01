package com.example.manymanager.domain

import java.time.Clock
import java.time.LocalDate

/**
 * Monthly civil-date periods beginning on an explicitly configured day in 1..28.
 * Days 29..31 are deliberately unsupported: no clamping or rollover policy is implied.
 * Callers supply the calculator setting and Clock; this class never reads storage.
 */
class BudgetPeriodCalculator(val periodStartDay: Int, private val clock: Clock) {
    init {
        require(periodStartDay in 1..28) { "periodStartDay must be in 1..28" }
    }

    fun periodContaining(date: LocalDate): BudgetPeriod {
        val boundary = date.withDayOfMonth(periodStartDay)
        val start = if (date < boundary) boundary.minusMonths(1) else boundary
        return periodStartingAt(start)
    }

    /** Uses the Clock's zone on every call, without converting civil dates through UTC. */
    fun currentPeriod(): BudgetPeriod = periodContaining(LocalDate.now(clock))

    fun previous(period: BudgetPeriod): BudgetPeriod {
        requireCompatible(period)
        return periodStartingAt(period.start.minusMonths(1))
    }

    fun next(period: BudgetPeriod): BudgetPeriod {
        requireCompatible(period)
        return periodStartingAt(period.endExclusive)
    }

    private fun periodStartingAt(start: LocalDate): BudgetPeriod =
        BudgetPeriod(start, start.plusMonths(1))

    private fun requireCompatible(period: BudgetPeriod) {
        require(period == periodContaining(period.start)) {
            "Period must match the configured start day and span exactly one calendar month"
        }
    }
}
