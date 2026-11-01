package com.example.manymanager

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.text.NumberFormat

/** Both panels consume one financial revision, never the independent choice-list streams. */
class SummaryBreakdownUi(private val root: View, private val onRetry: () -> Unit) {
    private val context = root.context

    fun render(state: PersistentDataState) {
        renderPanel(state, R.id.categorySummaryRows, R.id.categorySummaryEmpty, true)
        renderPanel(state, R.id.sourceSummaryRows, R.id.sourceSummaryEmpty, false)
    }

    private fun renderPanel(state: PersistentDataState, rowsId: Int, statusId: Int, category: Boolean) {
        val rows = root.findViewById<LinearLayout>(rowsId)
        rows.removeAllViews()
        val status = root.findViewById<TextView>(statusId)
        val message = when (state) {
            PersistentDataState.Loading -> R.string.expenses_loading
            is PersistentDataState.Failed -> R.string.expenses_load_error
            is PersistentDataState.Ready -> if (state.snapshot.expenseCount == 0) R.string.no_expenses else null
        }
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(status::setText)
        status.isClickable = state is PersistentDataState.Failed
        status.isFocusable = status.isClickable
        status.setOnClickListener { if (state is PersistentDataState.Failed) onRetry() }
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        if (state !is PersistentDataState.Ready) return
        val snapshot = state.snapshot
        if (category) {
            val names = snapshot.categories.associateBy { it.id }
            snapshot.categoryBreakdown.forEach {
                addRow(rows, it.categoryId, names[it.categoryId]?.name ?: context.getString(R.string.uncategorized),
                    it.spentMinor, it.expenseCount)
            }
        } else {
            snapshot.sourceBreakdown.forEach {
                addRow(rows, it.source.id, it.source.name, it.spentMinor, it.expenseCount)
            }
        }
    }

    private fun addRow(rows: LinearLayout, id: String?, name: String, spent: Long, count: Int) {
        val row = LayoutInflater.from(context).inflate(R.layout.row_summary_breakdown, rows, false)
        val locale = context.resources.configuration.locales[0]
        val amount = context.getString(R.string.ils_amount, BudgetAmounts.number(spent, locale))
        val countLabel = context.getString(R.string.breakdown_expense_count,
            NumberFormat.getIntegerInstance(locale).format(count))
        row.tag = id
        row.findViewById<TextView>(R.id.breakdownName).text = name
        row.findViewById<TextView>(R.id.breakdownAmount).text = amount
        row.findViewById<TextView>(R.id.breakdownCount).text = countLabel
        row.contentDescription = "$name · $amount · $countLabel"
        rows.addView(row)
    }
}
