package com.example.manymanager

import android.view.View
import android.view.LayoutInflater
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
    fun renderRows(rows: LinearLayout, snapshot: com.example.manymanager.domain.BudgetSnapshot, expenses: List<Expense>, groupCategory: Boolean? = null) {
        rows.removeAllViews()
        val categories = snapshot.categories.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.source.id }
        val locale = context.resources.configuration.locales[0]
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        expenses.forEachIndexed { index, expense ->
            val amount = context.getString(R.string.ils_amount, BudgetAmounts.number(expense.amountMinor, locale))
            val category = categories[expense.categoryId]?.name ?: context.getString(R.string.uncategorized)
            val source = sources[expense.sourceId]?.source?.name.orEmpty()
            val secondary = (listOf(expense.date.format(dateFormat)) + when (groupCategory) {
                true -> listOf(source)
                false -> listOf(category)
                null -> listOf(category, source)
            }).filter(String::isNotBlank).joinToString(" · ")
            val row = LayoutInflater.from(context).inflate(R.layout.row_summary_expense, rows, false)
            row.tag = expense.id
            row.findViewById<View>(R.id.summaryExpenseDivider).visibility =
                if (index > 0 || groupCategory != null) View.VISIBLE else View.GONE
            row.findViewById<TextView>(R.id.summaryExpenseAmount).text = amount
            row.findViewById<TextView>(R.id.summaryExpenseSecondary).text = secondary
            row.findViewById<TextView>(R.id.summaryExpenseNote).apply {
                text = expense.note.orEmpty()
                visibility = if (expense.note.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            row.isFocusable = true
            row.isClickable = true
            row.contentDescription = listOfNotNull(amount, secondary, expense.note?.takeIf(String::isNotBlank),
                context.getString(R.string.expense_details)).joinToString(" · ")
            val pressed = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, pressed, true)
            row.foreground = context.getDrawable(pressed.resourceId)
            row.setOnClickListener { onCorrect(expense) }
            rows.addView(row)
        }
    }
}
