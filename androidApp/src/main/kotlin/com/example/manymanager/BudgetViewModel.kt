package com.example.simple.manymanager

import androidx.lifecycle.ViewModel

/** Presentation state only; all business data belongs to PersistentDataViewModel. */
class BudgetViewModel : ViewModel() {
    var breakdownExpanded = false
}
