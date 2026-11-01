package com.example.manymanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CalculatorEntity::class, CategoryEntity::class, BudgetSourceEntity::class,
        BudgetAllocationEntity::class, ExpenseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class ManyManagerDatabase : RoomDatabase() {
    abstract fun dao(): ManyManagerDao

    companion object {
        private const val DATABASE_NAME = "many-manager.db"

        /** Owned once by the Application. Future schema versions require tested migrations. */
        fun create(context: Context): ManyManagerDatabase =
            Room.databaseBuilder(
                context.applicationContext, ManyManagerDatabase::class.java, DATABASE_NAME
            ).build()
    }
}
