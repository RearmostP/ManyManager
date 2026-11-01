package com.example.manymanager

import androidx.lifecycle.ViewModel
import java.time.YearMonth

/** Temporary UI model: survives configuration changes, but not process death. */
class BudgetViewModel : ViewModel() {
    val allocations = MonthlyBudgetAllocations(ExpenseUiData.paymentMethods)
    var homeMonth: YearMonth = YearMonth.now()
    var managementMonth: YearMonth = homeMonth
    var breakdownExpanded = false

}
