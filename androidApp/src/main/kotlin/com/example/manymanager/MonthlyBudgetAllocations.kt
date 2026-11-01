package com.example.manymanager

import java.time.YearMonth
import java.util.UUID

/** Temporary source identities and monthly allocations, retained only by the Activity ViewModel. */
class MonthlyBudgetAllocations(initialSources: List<PaymentSource>) {
    private val mutableSources = initialSources.toMutableList()
    val sources: List<PaymentSource> get() = mutableSources.toList()
    private val months = mutableMapOf<YearMonth, Map<String, Long>>()

    init {
        val ids = initialSources.map { it.id }
        require(ids.size == ids.toSet().size && ids.none { it.isBlank() })
    }

    fun amount(month: YearMonth, sourceId: String): Long = months[month]?.get(sourceId) ?: 0L

    fun total(month: YearMonth): Long = months[month].orEmpty().values
        .fold(0L) { sum, amount -> Math.addExact(sum, amount) }

    /** Existing allocation policy: no negative allocation and no overflow. */
    fun apply(month: YearMonth, sourceId: String, minorUnits: Long): Boolean {
        if (mutableSources.none { it.id == sourceId } || minorUnits < 0) return false
        val updated = months[month].orEmpty() + (sourceId to minorUnits)
        return try {
            updated.values.fold(0L) { sum, amount -> Math.addExact(sum, amount) }
            months[month] = updated
            true
        } catch (_: ArithmeticException) {
            false
        }
    }

    fun addSource(name: String, month: YearMonth, initialAmount: Long): PaymentSource? {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || initialAmount < 0) return null
        val source = PaymentSource(UUID.randomUUID().toString(), cleanName)
        // Validate before adding the identity, so failed creation leaves no partial source.
        val updated = months[month].orEmpty() + (source.id to initialAmount)
        try {
            updated.values.fold(0L) { sum, amount -> Math.addExact(sum, amount) }
        } catch (_: ArithmeticException) {
            return null
        }
        mutableSources.add(source)
        months[month] = updated
        return source
    }

    fun renameSource(sourceId: String, name: String): Boolean {
        val cleanName = name.trim()
        val index = mutableSources.indexOfFirst { it.id == sourceId }
        if (index < 0 || cleanName.isEmpty()) return false
        mutableSources[index] = mutableSources[index].copy(name = cleanName)
        return true
    }

    /** Signed preview is allowed; apply() enforces the existing allocation policy. */
    fun previewAdjustment(month: YearMonth, sourceId: String, delta: Long): Long? =
        if (mutableSources.none { it.id == sourceId }) null else try {
            Math.addExact(amount(month, sourceId), delta)
        } catch (_: ArithmeticException) {
            null
        }

    fun adjust(month: YearMonth, sourceId: String, delta: Long): Boolean {
        val preview = previewAdjustment(month, sourceId, delta) ?: return false
        return apply(month, sourceId, preview)
    }
}
