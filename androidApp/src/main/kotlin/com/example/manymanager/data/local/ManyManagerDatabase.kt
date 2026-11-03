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
    version = 2,
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

        /** Owned once by the Application. */
        fun create(context: Context): ManyManagerDatabase =
            Room.databaseBuilder(
                context.applicationContext, ManyManagerDatabase::class.java, DATABASE_NAME
            ).addMigrations(MIGRATION_1_2).build()
    }
}
