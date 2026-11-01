package com.example.manymanager.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "calculators")
data class CalculatorEntity(
    @PrimaryKey val id: String,
    val periodStartDay: Int = 10,
    val currencyCode: String = "ILS",
)

@Entity(
    tableName = "categories",
    primaryKeys = ["calculatorId", "id"],
    foreignKeys = [ForeignKey(
        entity = CalculatorEntity::class,
        parentColumns = ["id"], childColumns = ["calculatorId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class CategoryEntity(val calculatorId: String, val id: String, val name: String)

@Entity(
    tableName = "budget_sources",
    primaryKeys = ["calculatorId", "id"],
    foreignKeys = [ForeignKey(
        entity = CalculatorEntity::class,
        parentColumns = ["id"], childColumns = ["calculatorId"],
        onDelete = ForeignKey.RESTRICT,
    )],
)
data class BudgetSourceEntity(val calculatorId: String, val id: String, val name: String)

/** Civil period start, supplied by the caller; no calendar-month or day-10 calculation here. */
@Entity(
    tableName = "budget_allocations",
    primaryKeys = ["calculatorId", "sourceId", "periodStartEpochDay"],
    foreignKeys = [ForeignKey(
        entity = BudgetSourceEntity::class,
        parentColumns = ["calculatorId", "id"], childColumns = ["calculatorId", "sourceId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index(value = ["calculatorId", "periodStartEpochDay"])],
)
data class BudgetAllocationEntity(
    val calculatorId: String,
    val sourceId: String,
    val periodStartEpochDay: Long,
    val allocatedMinor: Long,
)

/** Money is integer minor units; dates are civil epoch days. Null category means uncategorized. */
@Entity(
    tableName = "expenses",
    primaryKeys = ["calculatorId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = CalculatorEntity::class,
            parentColumns = ["id"], childColumns = ["calculatorId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["calculatorId", "id"], childColumns = ["calculatorId", "categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = BudgetSourceEntity::class,
            parentColumns = ["calculatorId", "id"], childColumns = ["calculatorId", "sourceId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["calculatorId", "dateEpochDay"]),
        Index(value = ["calculatorId", "categoryId"]),
        Index(value = ["calculatorId", "sourceId"]),
    ],
)
data class ExpenseEntity(
    val calculatorId: String,
    val id: String,
    val amountMinor: Long,
    val dateEpochDay: Long,
    val categoryId: String?,
    val sourceId: String,
    val note: String? = null,
)
