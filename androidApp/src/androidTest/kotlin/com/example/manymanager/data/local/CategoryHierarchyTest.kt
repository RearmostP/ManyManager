package com.example.simple.manymanager.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.simple.manymanager.data.ManyManagerRepository
import com.example.simple.manymanager.domain.*
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryHierarchyTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), ManyManagerDatabase::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun populatedTwoToThreePreservesAllFactsAndAddsOnlyNullableParent() = runBlocking {
        for (version in listOf(1, 2)) {
            val name = "hierarchy-migration-${UUID.randomUUID()}.db"
            try {
                helper.createDatabase(name, version).use { db ->
                    db.execSQL("INSERT INTO calculators VALUES ('a',10,'ILS'),('b',15,'EUR')")
                    db.execSQL("INSERT INTO categories VALUES ('a','food','Food'),('b','food','Other')")
                    db.execSQL("INSERT INTO budget_sources VALUES ('a','cash','Cash'),('b','cash','Other cash')")
                    if (version == 1) db.execSQL("INSERT INTO budget_allocations VALUES ('a','cash',100,500),('b','cash',131,600)")
                    else db.execSQL("INSERT INTO period_budgets VALUES ('a',100,500),('b',131,600)")
                    db.execSQL("INSERT INTO expenses VALUES ('a','direct',123,101,'food','cash','Keep note'),('a','legacy',45,102,NULL,'cash',NULL),('b','other',67,132,'food','cash','Other note')")
                }
                helper.runMigrationsAndValidate(name, 3, true,
                    ManyManagerDatabase.MIGRATION_1_2, ManyManagerDatabase.MIGRATION_2_3).close()
                val db = Room.databaseBuilder(context, ManyManagerDatabase::class.java, name)
                    .addCallback(ManyManagerDatabase.HIERARCHY_CALLBACK).build()
                try {
                    assertEquals(listOf(CategoryEntity("a","food","Food")), db.dao().categories("a"))
                    assertEquals(ExpenseEntity("a","direct",123,101,"food","cash","Keep note"), db.dao().expense("a","direct"))
                    assertEquals(ExpenseEntity("a","legacy",45,102,null,"cash"), db.dao().expense("a","legacy"))
                    assertEquals(ExpenseEntity("b","other",67,132,"food","cash","Other note"), db.dao().expense("b","other"))
                    assertEquals(500L, db.dao().periodBudget("a",100)!!.amountMinor)
                    assertEquals(600L, db.dao().periodBudget("b",131)!!.amountMinor)
                    assertEquals(CalculatorEntity("b",15,"EUR"), db.dao().calculator("b"))
                    assertEquals(listOf(BudgetSourceEntity("a","cash","Cash")), db.dao().sources("a"))
                    db.dao().insertCategory(CategoryEntity("a","child","Shop","food"))
                    assertEquals("food", db.dao().expense("a","direct")!!.categoryId)
                    db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                } finally { db.close() }
            } finally { context.deleteDatabase(name) }
        }
    }

    @Test fun repositoryAndDatabaseRejectInvalidHierarchyAndRequireExplicitCategoryForLegacyEdit() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ManyManagerDatabase::class.java)
            .addCallback(ManyManagerDatabase.HIERARCHY_CALLBACK).build()
        try {
            val calculator = db.dao().initializeLocalCalculator()
            val repo = ManyManagerRepository(db, CompletableDeferred(Result.success(calculator)), Clock.systemUTC())
            fun <T> saved(result: DataResult<T>): T = (result as DataResult.Success).value
            val parent = saved(repo.createCategory("Food"))
            val child = saved(repo.createCategory("Food", parent.id))
            val method = saved(repo.createPaymentMethod("Cash"))
            val date = LocalDate.now()
            assertTrue(repo.createCategory("Deep", child.id) is DataResult.Failure)
            assertTrue(repo.createCategory("Missing", "missing") is DataResult.Failure)
            assertTrue(repo.createCategory(" ", parent.id) is DataResult.Failure)
            db.openHelper.writableDatabase.execSQL("INSERT INTO calculators VALUES ('other',10,'ILS')")
            db.dao().insertCategory(CategoryEntity("other","foreign","Other"))
            assertTrue(repo.createCategory("Foreign", "foreign") is DataResult.Failure)
            for ((id, parentId) in listOf("deep" to child.id, "self" to "self", "missing" to "missing", "foreign" to "foreign")) {
                try {
                    db.dao().insertCategory(CategoryEntity(calculator.id,id,"Invalid",parentId))
                    fail("Invalid hierarchy accepted: $id")
                } catch (_: SQLiteConstraintException) { }
            }
            try {
                db.openHelper.writableDatabase.execSQL("UPDATE categories SET parentId = ? WHERE id = ?", arrayOf(child.id,parent.id))
                fail("Cycle accepted")
            } catch (_: SQLiteConstraintException) { }
            val legacy = ExpenseEntity(calculator.id,"legacy",50,date.toEpochDay(),null,method.id,"Historical")
            db.dao().insertExpense(legacy)
            val invalid = NewExpense(50,date,method.id,null,"Historical")
            assertTrue(repo.createExpense(invalid) is DataResult.Failure)
            assertTrue(repo.updateExpense("legacy",invalid) is DataResult.Failure)
            assertNull(saved(repo.expense("legacy")).categoryId)
            assertEquals(50L, saved(repo.snapshot()).totalSpentMinor)
            val direct = saved(repo.createExpense(NewExpense(100,date,method.id,parent.id)))
            val nested = saved(repo.createExpense(NewExpense(250,date,method.id,child.id)))
            assertEquals(CategorySummary(parent.id,350,2), saved(repo.snapshot()).categoryBreakdown.first())
            val edited = saved(repo.updateExpense("legacy",invalid.copy(categoryId = child.id)))
            assertEquals("legacy",edited.id)
            assertEquals(CategorySummary(parent.id,400,3), saved(repo.snapshot()).categoryBreakdown.single())
            db.openHelper.writableDatabase.execSQL("UPDATE categories SET name = 'Renamed' WHERE id = ?", arrayOf(child.id))
            assertEquals(child.id,saved(repo.expense(nested.id)).categoryId)
            assertEquals(parent.id,saved(repo.expense(direct.id)).categoryId)
            assertEquals("Food / Renamed", saved(repo.snapshot()).categories.categoryPath(child.id))
        } finally { db.close() }
    }
}
