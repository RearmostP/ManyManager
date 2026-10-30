package com.example.manymanager

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class BudgetAmountsTest {
    @Test fun acceptsZeroAndExactDecimalAmounts() {
        assertEquals(0L, BudgetAmounts.parse("0"))
        assertEquals(12345L, BudgetAmounts.parse("123.45"))
        assertEquals(12345L, BudgetAmounts.parse("123,45"))
        assertEquals(50L, BudgetAmounts.parse(".5"))
        assertEquals(50L, BudgetAmounts.parse(",5"))
        assertEquals(123L, BudgetAmounts.parse(" 1.23 "))
        assertEquals(123L, BudgetAmounts.parse("١.٢٣"))
    }

    @Test fun rejectsNegativeMalformedAndOverpreciseValues() {
        listOf("", "-1", "+1", "1.234", "1,234", "1.", "1,", ".", "1,2.3",
            "1e2", "NaN", "12x", "1 000", "1\u00a0000", "1\n2").forEach {
            assertNull("Unexpected acceptance of $it", BudgetAmounts.parse(it))
        }
    }

    @Test fun rejectsOverflowWithoutLosingPrecision() {
        assertEquals(Long.MAX_VALUE, BudgetAmounts.parse("92233720368547758.07"))
        assertNull(BudgetAmounts.parse("92233720368547758.08"))
    }

    @Test fun roundTripsAllSupportedLocalesWithoutGrouping() {
        listOf("he", "en", "ru").forEach { tag ->
            val locale = Locale.forLanguageTag(tag)
            listOf(0L, 1L, 12345L, 100000L, Long.MAX_VALUE).forEach { amount ->
                assertEquals(amount, BudgetAmounts.parse(BudgetAmounts.number(amount, locale, false)))
            }
        }
    }

    @Test fun preservesWholeNumberStyleWithoutForcedDecimals() {
        assertEquals("0", BudgetAmounts.number(0, Locale.ENGLISH))
        assertEquals("12.5", BudgetAmounts.number(1250, Locale.ENGLISH))
    }
}
