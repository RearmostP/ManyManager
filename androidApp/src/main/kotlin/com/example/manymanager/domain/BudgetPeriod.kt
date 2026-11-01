package com.example.manymanager.domain

import java.time.LocalDate

/** Civil date interval [start, endExclusive), independent of storage and localization. */
data class BudgetPeriod(val start: LocalDate, val endExclusive: LocalDate) {
    init {
        require(start < endExclusive) { "Period start must precede endExclusive" }
    }

    val startEpochDay: Long get() = start.toEpochDay()
    val endExclusiveEpochDay: Long get() = endExclusive.toEpochDay()

    /** Stable period identity within a calculator; never a formatted month or timestamp. */
    val id: Long get() = startEpochDay

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive
}
