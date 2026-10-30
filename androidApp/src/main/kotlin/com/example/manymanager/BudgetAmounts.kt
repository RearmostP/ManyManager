package com.example.manymanager

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Exact ILS amounts in agorot. Input accepts decimal points and decimal commas. */
object BudgetAmounts {
    fun parse(input: String): Long? {
        val normalized = buildString {
            input.trim().forEach { char ->
                val digit = Character.digit(char, 10)
                append(if (digit >= 0) ('0'.code + digit).toChar() else char)
            }
        }
        if (!Regex("(?:[0-9]+(?:[.,][0-9]{1,2})?|[.,][0-9]{1,2})").matches(normalized)) return null
        return try {
            BigDecimal(normalized.replace(',', '.')).movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun number(minorUnits: Long, locale: Locale, grouping: Boolean = true): String =
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = grouping
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }.format(BigDecimal.valueOf(minorUnits, 2))
}
