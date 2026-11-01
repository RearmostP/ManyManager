package com.example.manymanager

import android.content.Context
import com.example.manymanager.domain.BudgetPeriod
import java.time.format.DateTimeFormatter

/** Formats a BudgetPeriod into a localized date range string. */
object BudgetPeriodFormatter {
    fun description(context: Context, period: BudgetPeriod): String {
        val formatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG)
            .withLocale(context.resources.configuration.locales[0])
        return context.getString(R.string.budget_period_description,
            period.start.format(formatter), period.endExclusive.minusDays(1).format(formatter))
    }

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
