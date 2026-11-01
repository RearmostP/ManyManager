package com.example.manymanager

import com.example.manymanager.domain.DataError
import com.example.manymanager.domain.DataResult
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Exact ILS amounts in agorot. Input accepts decimal points and decimal commas. */
object BudgetAmounts {
    fun parse(input: String): Long? = (parseResult(input) as? DataResult.Success)?.value

    fun positive(input: String): DataResult<Long> {
        if (input.trim().startsWith("-")) return DataResult.Failure(DataError.NonPositiveAmount)
        return when (val parsed = parseResult(input)) {
            is DataResult.Success -> if (parsed.value > 0) parsed else DataResult.Failure(DataError.NonPositiveAmount)
            is DataResult.Failure -> parsed
        }
    }

    fun parseResult(input: String): DataResult<Long> {
        val normalized = buildString {
            input.trim().forEach { char ->
                val digit = Character.digit(char, 10)
                append(if (digit >= 0) ('0'.code + digit).toChar() else char)
            }
        }
        if (!Regex("(?:[0-9]+(?:[.,][0-9]{1,2})?|[.,][0-9]{1,2})").matches(normalized)) return DataResult.Failure(DataError.InvalidAmount)
        return try {
            DataResult.Success(BigDecimal(normalized.replace(',', '.')).movePointRight(2).longValueExact())
        } catch (_: ArithmeticException) {
            DataResult.Failure(DataError.AmountOverflow)
        } catch (_: NumberFormatException) {
            DataResult.Failure(DataError.InvalidAmount)
        }
    }

    fun number(minorUnits: Long, locale: Locale, grouping: Boolean = true): String =
        NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = grouping
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }.format(BigDecimal.valueOf(minorUnits, 2))
}
