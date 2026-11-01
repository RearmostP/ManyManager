package com.example.manymanager

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.manymanager.domain.BudgetPeriod
import com.example.manymanager.domain.BudgetSnapshot
import com.example.manymanager.domain.BudgetSource

/** Displays the selected period's allocations from persistent storage. */
class BudgetManagementUi(
    private val root: View,
    private val onPreviousPeriod: () -> Unit,
    private val onNextPeriod: () -> Unit,
    private val onAddSource: (BudgetPeriod) -> Unit,
    private val onEditSource: (BudgetSource, BudgetPeriod, Long) -> Unit,
) {
    private val context = root.context
    private val locale get() = context.resources.configuration.locales[0]
    private var currentSnapshot: BudgetSnapshot? = null

    init {
        root.findViewById<View>(R.id.budgetPreviousMonth).setOnClickListener {
            onPreviousPeriod()
        }
        root.findViewById<View>(R.id.budgetNextMonth).setOnClickListener {
            onNextPeriod()
        }
        root.findViewById<View>(R.id.budgetAddSource).setOnClickListener {
            currentSnapshot?.let { onAddSource(it.period) }
        }
    }

    private fun money(amount: Long) =
        context.getString(R.string.ils_amount, BudgetAmounts.number(amount, locale))

    fun renderState(state: PersistentDataState, onRetry: () -> Unit) {
        listOf(R.id.budgetPreviousMonth, R.id.budgetNextMonth, R.id.budgetAddSource).forEach {
            root.findViewById<View>(it).isEnabled = state is PersistentDataState.Ready
        }
        if (state is PersistentDataState.Ready) {
            root.findViewById<View>(R.id.budgetNoSources).apply {
                setOnClickListener(null)
                isClickable = false
                isFocusable = false
            }
            render(state.snapshot)
            return
        }
        currentSnapshot = null
        root.findViewById<LinearLayout>(R.id.budgetSourceRows).removeAllViews()
        root.findViewById<TextView>(R.id.budgetAllocatedTotal).text = "—"
        root.findViewById<TextView>(R.id.budgetNoSources).apply {
            visibility = View.VISIBLE
            setText(if (state is PersistentDataState.Failed) R.string.budget_load_error else R.string.budget_loading)
            setOnClickListener { onRetry() }
            isClickable = state is PersistentDataState.Failed
            isFocusable = isClickable
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
    }

    fun render(snapshot: BudgetSnapshot) {
        root.findViewById<TextView>(R.id.budgetNoSources).setText(R.string.no_budget_sources_yet)
        currentSnapshot = snapshot
        val period = snapshot.period
        root.findViewById<TextView>(R.id.budgetMonth).apply {
            text = BudgetPeriodFormatter.format(context, period)
            contentDescription = BudgetPeriodFormatter.description(context, period)
        }
        root.findViewById<TextView>(R.id.budgetAllocatedTotal).text =
            money(snapshot.totalAllocatedMinor)
        val sources = snapshot.sources
        root.findViewById<View>(R.id.budgetNoSources).visibility =
            if (sources.isEmpty()) View.VISIBLE else View.GONE
        val rows = root.findViewById<LinearLayout>(R.id.budgetSourceRows)
        rows.removeAllViews()
        sources.forEach { sourceBudget ->
            val source = sourceBudget.source
            val allocated = sourceBudget.allocatedMinor
            val row = LayoutInflater.from(context).inflate(R.layout.row_budget_source, rows, false)
            row.findViewById<TextView>(R.id.budgetSourceName).text = source.name
            row.findViewById<TextView>(R.id.budgetSourceAmount).text = money(allocated)
            row.contentDescription = context.getString(
                R.string.budget_amount_for_source, source.name
            ) + ": " + money(allocated)
            row.setOnClickListener { onEditSource(source, period, allocated) }
            rows.addView(row)
        }
    }
}
