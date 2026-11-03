package com.example.manymanager

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.manymanager.domain.Expense
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class AllExpensesUi(private val root: View, private val onCorrect: (Expense) -> Unit,
    private val onRetry: () -> Unit,
    private val rowsId: Int = R.id.expenseRows,
    private val statusId: Int = R.id.expensesEmpty,
    private val emptyMessage: Int = R.string.no_expenses) {
    private val context = root.context
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun render(state: PersistentDataState, includes: (Expense) -> Boolean = { true }) {
        val rows = root.findViewById<LinearLayout>(rowsId)
        val status = root.findViewById<TextView>(statusId)
        rows.removeAllViews()
        val snapshot = (state as? PersistentDataState.Ready)?.snapshot
        val expenses = snapshot?.expenses?.filter(includes).orEmpty()
        val message = when (state) {
            is PersistentDataState.Loading -> R.string.expenses_loading
            is PersistentDataState.Failed -> R.string.expenses_load_error
            is PersistentDataState.Ready -> if (expenses.isEmpty()) emptyMessage else null
        }
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(status::setText)
        status.isClickable = state is PersistentDataState.Failed
        status.isFocusable = status.isClickable
        status.setOnClickListener { if (state is PersistentDataState.Failed) onRetry() }
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        if (snapshot == null) return
        renderRows(rows, snapshot, expenses)
    }

    /** Shared non-scrolling rows for All Expenses and inline Summary groups. */
    fun renderRows(rows: LinearLayout, snapshot: com.example.manymanager.domain.BudgetSnapshot, expenses: List<Expense>) {
        rows.removeAllViews()
        val categories = snapshot.categories.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.source.id }
        val locale = context.resources.configuration.locales[0]
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        expenses.forEach { expense ->
            val labels = listOf(
                context.getString(R.string.ils_amount, BudgetAmounts.number(expense.amountMinor, locale)),
                expense.date.format(dateFormat),
                categories[expense.categoryId]?.name ?: context.getString(R.string.uncategorized),
                sources[expense.sourceId]?.source?.name.orEmpty(),
            ) + listOfNotNull(expense.note?.takeIf { it.isNotBlank() })
            rows.addView(TextView(context, null, 0, R.style.Text).apply {
                text = labels.joinToString(" · ")
                tag = expense.id
                textSize = 14f
                minHeight = dp(48)
                setPadding(dp(14), dp(14), dp(14), dp(14))
                setTextColor(context.getColor(R.color.text))
                setBackgroundResource(R.drawable.chip)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) }
                isFocusable = true
                contentDescription = text.toString() + ". " + context.getString(R.string.expense_details)
                setOnClickListener { onCorrect(expense) }
            })
        }
    }
}
