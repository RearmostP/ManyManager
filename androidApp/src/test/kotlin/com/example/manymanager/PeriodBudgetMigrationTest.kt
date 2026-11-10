package com.example.manymanager

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.manymanager.data.local.ManyManagerDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

/** Executes the registered migration's aggregation and writes; real SQLite validation is in androidTest. */
class PeriodBudgetMigrationTest {
    private data class Row(val calculator: String, val period: Long, val amount: Long)
    private data class Recorded(val sql: String, val args: List<Any?>)

    private fun runMigration(rows: List<Row>): List<Recorded> {
        var position = -1
        val writes = mutableListOf<Recorded>()
        val cursor = Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) { _, method, args ->
            when (method.name) {
                "moveToNext" -> ++position < rows.size
                "getString" -> rows[position].calculator
                "getLong" -> if (args!![0] == 1) rows[position].period else rows[position].amount
                "close" -> null
                else -> error("Unexpected cursor operation: ${method.name}")
            }
        } as Cursor
        val db = Proxy.newProxyInstance(SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            when (method.name) {
                "query" -> {
                    assertEquals("SELECT calculatorId, periodStartEpochDay, allocatedMinor FROM budget_allocations", args!![0])
                    cursor
                }
                "execSQL" -> {
                    writes += Recorded(args!![0] as String,
                        (args.getOrNull(1) as? Array<*>)?.toList().orEmpty())
                    null
                }
                else -> error("Unexpected database operation: ${method.name}")
            }
        } as SupportSQLiteDatabase
        ManyManagerDatabase.MIGRATION_1_2.migrate(db)
        return writes
    }

    @Test fun onlyRepresentedPeriodsAreInitializedAndSignedAmountsStayExact() {
        val writes = runMigration(listOf(
            Row("local", 10, 80000), Row("local", 10, 420000),
            Row("local", 41, 0), Row("local", 41, -123),
            Row("local", 72, 0), Row("other", 10, 17)))
        assertTrue(writes.first().sql.startsWith("CREATE TABLE IF NOT EXISTS period_budgets"))
        val inserts = writes.drop(1)
        assertTrue(inserts.all { it.sql == "INSERT INTO period_budgets VALUES (?, ?, ?)" })
        assertEquals(listOf(
            listOf("local", 10L, 500000L), listOf("local", 41L, -123L),
            listOf("local", 72L, 0L), listOf("other", 10L, 17L)), inserts.map { it.args })
        // No SQL updates/deletes any existing table, source, expense, ID or settings.
        assertEquals(5, writes.size)
    }

    @Test fun intermediateOverflowDoesNotChangeFinalHistoricalAmount() {
        val writes = runMigration(listOf(Row("local", 10, Long.MAX_VALUE),
            Row("local", 10, 1), Row("local", 10, -1)))
        assertEquals(listOf("local", 10L, Long.MAX_VALUE), writes.last().args)
    }

    @Test fun unrepresentableTotalFailsInsteadOfRoundingOrClamping() {
        assertThrows(IllegalStateException::class.java) {
            runMigration(listOf(Row("local", 10, Long.MAX_VALUE), Row("local", 10, 1)))
        }
    }

    @Test fun emptyLegacyDatabaseCreatesNoBudgetRows() {
        assertEquals(1, runMigration(emptyList()).size)
    }
}
