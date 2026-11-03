package com.example.simple.manymanager

import android.os.Bundle
import com.example.simple.manymanager.domain.*
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
        putString("note", note); putString("categoryId", categoryId); putString("sourceId", sourceId)
    }
    companion object {
        fun fromBundle(bundle: Bundle) = ExpenseDraft(
            bundle.getString("amount").orEmpty(),
            if (bundle.containsKey("dateEpochDay")) LocalDate.ofEpochDay(bundle.getLong("dateEpochDay")) else null,
            bundle.getString("note").orEmpty(), bundle.getString("categoryId"), bundle.getString("sourceId"))
        fun fromExpense(expense: Expense) = ExpenseDraft(
            BudgetAmounts.number(expense.amountMinor, java.util.Locale.ROOT, grouping = false),
            expense.date, expense.note.orEmpty(), expense.categoryId, expense.sourceId)
    }
}

/** Controller state: creation form or saved details with a separate in-place edit draft. */
data class ExpenseCreationState(
    val draft: ExpenseDraft,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val error: DataError? = null,
    val interrupted: Boolean = false,
    val expenseId: String? = null,
    val original: Expense? = null,
    val loading: Boolean = false,
    val confirmingDelete: Boolean = false,
    val editing: Boolean = false,
) {
    fun toBundle() = draft.toBundle().apply {
        putBoolean("submitting", submitting); putBoolean("saved", saved); putBoolean("interrupted", interrupted)
        putString("expenseId", expenseId); putBoolean("loading", loading)
        putBoolean("confirmingDelete", confirmingDelete); putBoolean("editing", editing)
        original?.let {
            putBundle("original", ExpenseDraft.fromExpense(it).toBundle().apply {
                putString("owner", it.calculatorId); putLong("amountMinor", it.amountMinor)
            })
        }
    }
    companion object {
        fun fromBundle(bundle: Bundle): ExpenseCreationState {
            val id = bundle.getString("expenseId")
            val original = bundle.getBundle("original")?.let {
                val draft = ExpenseDraft.fromBundle(it)
                if (id == null || draft.date == null || draft.sourceId == null) null
                else Expense(it.getString("owner").orEmpty(), id, it.getLong("amountMinor"),
                    draft.date, draft.categoryId, draft.sourceId, draft.note.takeIf(String::isNotEmpty))
            }
            val interrupted = bundle.getBoolean("submitting") || bundle.getBoolean("interrupted")
            return ExpenseCreationState(ExpenseDraft.fromBundle(bundle), saved = bundle.getBoolean("saved"),
                interrupted = interrupted, expenseId = id, original = original,
                loading = bundle.getBoolean("loading"), confirmingDelete = !interrupted && bundle.getBoolean("confirmingDelete"),
                editing = bundle.getBoolean("editing"))
        }
    }
}

fun expenseErrorMessage(error: DataError): Int = when (error) {
    DataError.InvalidAmount, DataError.NonPositiveAmount -> R.string.expense_positive_amount
    DataError.AmountOverflow -> R.string.expense_amount_overflow
    DataError.InvalidDate, DataError.InvalidPeriod -> R.string.expense_invalid_date
    is DataError.MissingEntity -> if (error.kind == EntityKind.EXPENSE) R.string.expense_missing_record else R.string.expense_missing_choice
    else -> R.string.expense_save_error
}
