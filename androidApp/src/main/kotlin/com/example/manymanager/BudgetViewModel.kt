package com.example.manymanager

import androidx.lifecycle.ViewModel
import java.time.YearMonth

/** Temporary UI model: survives configuration changes, but not process death. */
class BudgetViewModel : ViewModel() {
    val allocations = MonthlyBudgetAllocations(ExpenseUiData.paymentMethods)
    val categories = ExpenseUiData.categories.toMutableList()
    var homeMonth: YearMonth = YearMonth.now()
    var managementMonth: YearMonth = homeMonth
    var breakdownExpanded = false

    fun addCategory(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false
        categories.add(cleanName)
        return true
    }
}
