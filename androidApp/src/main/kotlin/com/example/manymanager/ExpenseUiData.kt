package com.example.manymanager

/** A display name is user data; its stable ID is independent of locale. */
data class PaymentSource(val id: String, val name: String)

/** Shared initial UI state until expense and option management are implemented. */
object ExpenseUiData {
    val paymentMethods: List<PaymentSource> = emptyList()
    const val expenseCount = 0
    const val monthlyTotal = 0L
}
