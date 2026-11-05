package com.example.simple.manymanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.simple.manymanager.domain.BudgetCalculations

@Database(
    entities = [
        CalculatorEntity::class, CategoryEntity::class, BudgetSourceEntity::class,
        PeriodBudgetEntity::class, ExpenseEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ManyManagerDatabase : RoomDatabase() {
    abstract fun dao(): ManyManagerDao

    companion object {
        private const val DATABASE_NAME = "many-manager.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS period_budgets (
                    calculatorId TEXT NOT NULL, periodStartEpochDay INTEGER NOT NULL,
                    amountMinor INTEGER NOT NULL,
                    PRIMARY KEY(calculatorId, periodStartEpochDay),
                    FOREIGN KEY(calculatorId) REFERENCES calculators(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                // Aggregate in Kotlin: SQLite SUM can overflow before the final exact-money check.
                val amounts = linkedMapOf<Pair<String, Long>, MutableList<Long>>()
                db.query("SELECT calculatorId, periodStartEpochDay, allocatedMinor FROM budget_allocations").use { cursor ->
                    while (cursor.moveToNext()) {
                        val amount = cursor.getLong(2)
                        require(amount >= 0) { "Negative legacy allocation" }
                        amounts.getOrPut(cursor.getString(0) to cursor.getLong(1)) { mutableListOf() }.add(amount)
                    }
                }
                amounts.forEach { (key, values) ->
                    db.execSQL("INSERT INTO period_budgets VALUES (?, ?, ?)",
                        arrayOf<Any>(key.first, key.second, BudgetCalculations.sum(values)))
                }
                db.execSQL("DROP TABLE budget_allocations")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Stage expenses before swapping their referenced categories table (RESTRICT is immediate).
                db.execSQL("CREATE TEMP TABLE saved_expenses AS SELECT * FROM expenses")
                db.execSQL("DROP TABLE expenses")
                db.execSQL("""CREATE TABLE categories_new (
                    calculatorId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, parentId TEXT,
                    PRIMARY KEY(calculatorId, id),
                    FOREIGN KEY(calculatorId) REFERENCES calculators(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(calculatorId, parentId) REFERENCES categories(calculatorId, id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("INSERT INTO categories_new (calculatorId, id, name) SELECT calculatorId, id, name FROM categories")
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")
                db.execSQL("CREATE INDEX index_categories_calculatorId_parentId ON categories(calculatorId, parentId)")
                db.execSQL("""CREATE TABLE expenses (
                    calculatorId TEXT NOT NULL, id TEXT NOT NULL, amountMinor INTEGER NOT NULL,
                    dateEpochDay INTEGER NOT NULL, categoryId TEXT, sourceId TEXT NOT NULL, note TEXT,
                    PRIMARY KEY(calculatorId, id),
                    FOREIGN KEY(calculatorId) REFERENCES calculators(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(calculatorId, categoryId) REFERENCES categories(calculatorId, id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(calculatorId, sourceId) REFERENCES budget_sources(calculatorId, id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("INSERT INTO expenses SELECT * FROM saved_expenses")
                db.execSQL("DROP TABLE saved_expenses")
                for (column in listOf("dateEpochDay", "categoryId", "sourceId"))
                    db.execSQL("CREATE INDEX index_expenses_calculatorId_$column ON expenses(calculatorId, $column)")
                installHierarchyConstraints(db)
            }
        }

        private fun installHierarchyConstraints(db: SupportSQLiteDatabase) {
            for (operation in listOf("INSERT", "UPDATE")) {
                db.execSQL("""CREATE TRIGGER IF NOT EXISTS categories_depth_${operation.lowercase()}
                    BEFORE $operation ON categories
                    WHEN NEW.parentId IS NOT NULL AND (
                        NEW.parentId = NEW.id OR
                        NOT EXISTS (SELECT 1 FROM categories p WHERE p.calculatorId = NEW.calculatorId
                            AND p.id = NEW.parentId AND p.parentId IS NULL) OR
                        EXISTS (SELECT 1 FROM categories c WHERE c.calculatorId = NEW.calculatorId AND c.parentId = NEW.id))
                    BEGIN SELECT RAISE(ABORT, 'Categories support exactly two levels'); END""")
            }
        }

        val HIERARCHY_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) = installHierarchyConstraints(db)
            override fun onOpen(db: SupportSQLiteDatabase) = installHierarchyConstraints(db)
        }

        /** Owned once by the Application. */
        fun create(context: Context): ManyManagerDatabase =
            Room.databaseBuilder(
                context.applicationContext, ManyManagerDatabase::class.java, DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(HIERARCHY_CALLBACK).build()
    }
}
