package com.example.manymanager

import android.os.Bundle
import com.example.manymanager.domain.*
import java.time.LocalDate

/** Raw amount text and a civil date; never a locale-dependent date string. */
data class ExpenseDraft(
    val amount: String = "",
    val date: LocalDate? = null,
    val note: String = "",
    val categoryId: String? = null,
    val sourceId: String? = null,
) {
    fun toBundle() = Bundle().apply {
        putString("amount", amount)
        date?.let { putLong("dateEpochDay", it.toEpochDay()) }
        putString("note", note)
        putString("categoryId", categoryId)
        putString("sourceId", sourceId)
    }
    companion object {
        fun fromBundle(bundle: Bundle) = ExpenseDraft(
            amount = bundle.getString("amount").orEmpty(),
            date = if (bundle.containsKey("dateEpochDay")) LocalDate.ofEpochDay(bundle.getLong("dateEpochDay")) else null,
            note = bundle.getString("note").orEmpty(),
            categoryId = bundle.getString("categoryId"), sourceId = bundle.getString("sourceId"),
        )
    }
}

data class ExpenseCreationState(
    val draft: ExpenseDraft,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val error: DataError? = null,
    val interrupted: Boolean = false,
) {
    fun toBundle() = draft.toBundle().apply {
        putBoolean("submitting", submitting); putBoolean("saved", saved); putBoolean("interrupted", interrupted)
    }
    companion object {
        fun fromBundle(bundle: Bundle) = ExpenseCreationState(ExpenseDraft.fromBundle(bundle),
            saved = bundle.getBoolean("saved"),
            interrupted = bundle.getBoolean("submitting") || bundle.getBoolean("interrupted"))
    }
}

data class ExpenseCorrectionState(
    val expense: Expense,
    val amountText: String,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val error: DataError? = null,
    val interrupted: Boolean = false,
) {
    fun toBundle() = ExpenseDraft(amountText, expense.date, expense.note.orEmpty(),
        expense.categoryId, expense.sourceId).toBundle().apply {
        putString("id", expense.id); putString("owner", expense.calculatorId)
        putLong("currentMinor", expense.amountMinor)
        putBoolean("submitting", submitting); putBoolean("saved", saved); putBoolean("interrupted", interrupted)
    }
    companion object {
        fun fromBundle(bundle: Bundle): ExpenseCorrectionState? {
            val id = bundle.getString("id") ?: return null
            val draft = ExpenseDraft.fromBundle(bundle)
            val date = draft.date ?: return null
            return ExpenseCorrectionState(
                Expense(bundle.getString("owner").orEmpty(), id, bundle.getLong("currentMinor"),
                    date, draft.categoryId, draft.sourceId ?: return null, draft.note.takeIf { it.isNotEmpty() }),
                draft.amount, saved = bundle.getBoolean("saved"),
                interrupted = bundle.getBoolean("submitting") || bundle.getBoolean("interrupted"))
        }
    }
}

fun expenseErrorMessage(error: DataError): Int = when (error) {
    DataError.InvalidAmount, DataError.NonPositiveAmount -> R.string.expense_positive_amount
    DataError.AmountOverflow -> R.string.expense_amount_overflow
    DataError.InvalidDate, DataError.InvalidPeriod -> R.string.expense_invalid_date
    is DataError.MissingEntity -> R.string.expense_missing_choice
    else -> R.string.expense_save_error
}
