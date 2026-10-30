package com.example.manymanager

import java.time.YearMonth

/** Temporary allocations keyed by calendar month and stable source ID, never display names. */
class MonthlyBudgetAllocations(sources: List<PaymentSource>) {
    val sources = sources.toList()
    private val sourceIds = sources.map { it.id }.toSet()
    private val months = mutableMapOf<YearMonth, Map<String, Long>>()

    init {
        require(sourceIds.size == sources.size && sourceIds.none { it.isBlank() })
    }

    fun amount(month: YearMonth, sourceId: String): Long = months[month]?.get(sourceId) ?: 0L

    fun total(month: YearMonth): Long = months[month].orEmpty().values
        .fold(0L) { sum, amount -> Math.addExact(sum, amount) }

    /** Reject unknown IDs, negatives and totals outside the supported exact integer range. */
    fun apply(month: YearMonth, sourceId: String, minorUnits: Long): Boolean {
        if (sourceId !in sourceIds || minorUnits < 0) return false
        val updated = months[month].orEmpty() + (sourceId to minorUnits)
        return try {
            updated.values.fold(0L) { sum, amount -> Math.addExact(sum, amount) }
            months[month] = updated
            true
        } catch (_: ArithmeticException) {
            false
        }
    }
}
