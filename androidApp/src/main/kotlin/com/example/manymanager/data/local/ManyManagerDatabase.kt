package com.example.manymanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CalculatorEntity::class, CategoryEntity::class, BudgetSourceEntity::class,
        BudgetAllocationEntity::class, ExpenseEntity::class, PeriodBudgetEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ManyManagerDatabase : RoomDatabase() {
    abstract fun dao(): ManyManagerDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS period_budgets (
                    calculatorId TEXT NOT NULL, periodStartEpochDay INTEGER NOT NULL,
                    amountMinor INTEGER NOT NULL, PRIMARY KEY(calculatorId, periodStartEpochDay),
                    FOREIGN KEY(calculatorId) REFERENCES calculators(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                // Keep every legacy allocation unchanged as a source limit, including zero/negative.
                // BigInteger avoids SQLite SUM overflow or floating point conversion.
                val totals = linkedMapOf<Pair<String, Long>, java.math.BigInteger>()
                db.query("SELECT calculatorId, periodStartEpochDay, allocatedMinor FROM budget_allocations").use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(0) to cursor.getLong(1)
                        totals[key] = (totals[key] ?: java.math.BigInteger.ZERO)
                            .add(java.math.BigInteger.valueOf(cursor.getLong(2)))
                    }
                }
                totals.forEach { (key, amount) ->
                    check(amount >= java.math.BigInteger.valueOf(Long.MIN_VALUE) &&
                        amount <= java.math.BigInteger.valueOf(Long.MAX_VALUE)) {
                        "Legacy period total does not fit Long; migration rolled back"
                    }
                    db.execSQL("INSERT INTO period_budgets VALUES (?, ?, ?)",
                        arrayOf<Any>(key.first, key.second, amount.toLong()))
                }
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE categories_new (
                    calculatorId TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, parentId TEXT,
                    PRIMARY KEY(calculatorId, id),
                    FOREIGN KEY(calculatorId) REFERENCES calculators(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(calculatorId, parentId) REFERENCES categories_new(calculatorId, id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("INSERT INTO categories_new (calculatorId,id,name) SELECT calculatorId,id,name FROM categories")
                // Rebuild referencing expenses before replacing categories; keep foreign keys enabled.
                db.execSQL("""CREATE TABLE expenses_new (
                    calculatorId TEXT NOT NULL, id TEXT NOT NULL, amountMinor INTEGER NOT NULL,
                    dateEpochDay INTEGER NOT NULL, categoryId TEXT, sourceId TEXT NOT NULL, note TEXT,
                    PRIMARY KEY(calculatorId,id),
                    FOREIGN KEY(calculatorId) REFERENCES calculators(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(calculatorId,categoryId) REFERENCES categories_new(calculatorId,id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(calculatorId,sourceId) REFERENCES budget_sources(calculatorId,id) ON UPDATE NO ACTION ON DELETE RESTRICT
                )""")
                db.execSQL("INSERT INTO expenses_new SELECT * FROM expenses")
                db.execSQL("DROP TABLE expenses")
                db.execSQL("DROP TABLE categories")
                db.execSQL("ALTER TABLE categories_new RENAME TO categories")
                db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")
                db.execSQL("CREATE INDEX index_categories_calculatorId_parentId ON categories(calculatorId,parentId)")
                listOf("dateEpochDay", "categoryId", "sourceId").forEach { column ->
                    db.execSQL("CREATE INDEX index_expenses_calculatorId_${column} ON expenses(calculatorId,$column)")
                }
            }
        }

        private const val DATABASE_NAME = "many-manager.db"

        /** Owned once by the Application. Future schema versions require tested migrations. */
        fun create(context: Context): ManyManagerDatabase =
            Room.databaseBuilder(
                context.applicationContext, ManyManagerDatabase::class.java, DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
