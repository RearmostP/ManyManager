package com.example.simple.manymanager.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleBudgetMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), ManyManagerDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private fun name() = "single-budget-migration-${UUID.randomUUID()}.db".also { names.add(it) }
    @After fun cleanup() { names.forEach { context.deleteDatabase(it) } }

    @Test fun populatedVersionOnePreservesAllIdentitiesAndPeriodTotals() = runBlocking {
        val name = name()
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO calculators VALUES ('a', 10, 'ILS'), ('b', 15, 'EUR')")
            db.execSQL("INSERT INTO categories VALUES ('a', 'food', 'Food')")
            db.execSQL("INSERT INTO budget_sources VALUES ('a','cash','Cash'),('a','card','Card'),('a','unused','Unused'),('b','cash','Other cash')")
            db.execSQL("INSERT INTO budget_allocations VALUES ('a','cash',100,1200000),('a','card',100,800000),('a','cash',131,12345),('a','card',131,0),('b','cash',100,99)")
            db.execSQL("INSERT INTO expenses VALUES ('a','e1',10000,109,'food','cash','Keep note'),('a','e2',25000,139,NULL,'card',NULL),('a','e3',1,140,NULL,'unused','No allocation')")
        }
        helper.runMigrationsAndValidate(name, 2, true, ManyManagerDatabase.MIGRATION_1_2).close()
        val db = Room.databaseBuilder(context, ManyManagerDatabase::class.java, name)
            .addMigrations(ManyManagerDatabase.MIGRATION_1_2).build()
        try {
            val dao = db.dao()
            assertEquals(2_000_000L, dao.periodBudget("a",100)!!.amountMinor)
            assertEquals(12345L, dao.periodBudget("a",131)!!.amountMinor)
            assertEquals(99L, dao.periodBudget("b",100)!!.amountMinor)
            assertNull(dao.periodBudget("a",162))
            assertEquals(listOf("cash","card","unused"), dao.sources("a").map { it.id })
            assertEquals(ExpenseEntity("a","e1",10000,109,"food","cash","Keep note"), dao.expense("a","e1"))
            assertEquals(ExpenseEntity("a","e2",25000,139,null,"card",null), dao.expense("a","e2"))
            assertEquals("unused", dao.expense("a","e3")!!.sourceId)
            assertEquals(listOf(CategoryEntity("a","food","Food")), dao.categories("a"))
            assertEquals(CalculatorEntity("b",15,"EUR"), dao.calculator("b"))
            db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { db.close() }
    }

    @Test fun maximumExactSumIsKeptAndOverflowAbortsWithoutReset() {
        for (overflow in listOf(false, true)) {
            val name = name()
            helper.createDatabase(name, 1).use { db ->
                db.execSQL("INSERT INTO calculators VALUES ('a',10,'ILS')")
                db.execSQL("INSERT INTO budget_sources VALUES ('a','x','X'),('a','y','Y')")
                db.execSQL("INSERT INTO budget_allocations VALUES ('a','x',100,?),('a','y',100,?)",
                    arrayOf<Any>(Long.MAX_VALUE - 1, if (overflow) 2L else 1L))
            }
            if (!overflow) {
                helper.runMigrationsAndValidate(name,2,true,ManyManagerDatabase.MIGRATION_1_2).use { db ->
                    db.query("SELECT amountMinor FROM period_budgets").use {
                        assertTrue(it.moveToFirst()); assertEquals(Long.MAX_VALUE,it.getLong(0))
                    }
                }
            } else {
                try {
                    helper.runMigrationsAndValidate(name,2,true,ManyManagerDatabase.MIGRATION_1_2).close()
                    fail("Overflow must abort migration")
                } catch (failure: Exception) {
                    assertTrue(generateSequence<Throwable>(failure) { it.cause }.any { it is ArithmeticException })
                }
                android.database.sqlite.SQLiteDatabase.openDatabase(
                    context.getDatabasePath(name).path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { db ->
                    assertEquals(1,db.version)
                    db.rawQuery("SELECT COUNT(*) FROM budget_allocations",null).use {
                        assertTrue(it.moveToFirst()); assertEquals(2,it.getInt(0))
                    }
                }
            }
        }
    }
}
