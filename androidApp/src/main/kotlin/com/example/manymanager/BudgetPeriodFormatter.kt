package com.example.manymanager

import android.content.Context
import com.example.manymanager.domain.BudgetPeriod
import java.time.format.DateTimeFormatter

/** Formats a BudgetPeriod into a localized date range string. */
object BudgetPeriodFormatter {
    fun format(context: Context, period: BudgetPeriod): String {
        val locale = context.resources.configuration.locales[0]
        val endDate = period.endExclusive.minusDays(1)
        return if (period.start.dayOfMonth == 1 && period.start.month == endDate.month) {
            period.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        } else if (period.start.year == endDate.year) {
            val startStr = period.start.format(DateTimeFormatter.ofPattern("d MMM", locale))
            val endStr = endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
            "$startStr – $endStr"
        } else {
            val startStr = period.start.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
            val endStr = endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
            "$startStr – $endStr"
        }
    }
}
