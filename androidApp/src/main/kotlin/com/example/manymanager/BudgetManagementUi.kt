package com.example.manymanager

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Displays the selected month's allocations; mutations live in MonthlyBudgetAllocations. */
class BudgetManagementUi(
    private val root: View,
    private val state: BudgetViewModel,
    onAddSource: (YearMonth) -> Unit,
    private val onEditSource: (PaymentSource, YearMonth) -> Unit
) {
    private val context = root.context
    private val locale get() = context.resources.configuration.locales[0]

    init {
        root.findViewById<View>(R.id.budgetPreviousMonth).setOnClickListener {
            state.managementMonth = state.managementMonth.minusMonths(1)
            render()
        }
        root.findViewById<View>(R.id.budgetNextMonth).setOnClickListener {
            state.managementMonth = state.managementMonth.plusMonths(1)
            render()
        }
        root.findViewById<View>(R.id.budgetAddSource).setOnClickListener {
            onAddSource(state.managementMonth)
        }
        render()
    }

    private fun money(amount: Long) =
        context.getString(R.string.ils_amount, BudgetAmounts.number(amount, locale))

    fun render() {
        val month = state.managementMonth
        root.findViewById<TextView>(R.id.budgetMonth).text =
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        root.findViewById<TextView>(R.id.budgetAllocatedTotal).text =
            money(state.allocations.total(month))
        val sources = state.allocations.sources
        root.findViewById<View>(R.id.budgetNoSources).visibility =
            if (sources.isEmpty()) View.VISIBLE else View.GONE
        val rows = root.findViewById<LinearLayout>(R.id.budgetSourceRows)
        rows.removeAllViews()
        sources.forEach { source ->
            val row = LayoutInflater.from(context).inflate(R.layout.row_budget_source, rows, false)
            row.findViewById<TextView>(R.id.budgetSourceName).text = source.name
            row.findViewById<TextView>(R.id.budgetSourceAmount).text =
                money(state.allocations.amount(month, source.id))
            row.contentDescription = context.getString(
                R.string.budget_amount_for_source, source.name
            ) + ": " + money(state.allocations.amount(month, source.id))
            row.setOnClickListener { onEditSource(source, month) }
            rows.addView(row)
        }
    }
}
