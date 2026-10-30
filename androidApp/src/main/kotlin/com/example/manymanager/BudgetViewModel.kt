package com.example.manymanager

import androidx.lifecycle.ViewModel
import java.time.YearMonth

/** Shared only for this Activity's lifetime, including ordinary configuration changes. */
class BudgetViewModel : ViewModel() {
    val allocations = MonthlyBudgetAllocations(ExpenseUiData.paymentMethods)
    var homeMonth: YearMonth = YearMonth.now()
    var managementMonth: YearMonth = homeMonth
    var breakdownExpanded = false

    data class DraftKey(val month: YearMonth, val sourceId: String)
    val drafts = mutableMapOf<DraftKey, String>()
}
