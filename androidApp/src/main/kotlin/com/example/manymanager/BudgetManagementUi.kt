package com.example.simple.manymanager

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.example.simple.manymanager.domain.BudgetPeriod
import com.example.simple.manymanager.domain.BudgetSnapshot
import com.example.simple.manymanager.domain.BudgetSource

/** One selected-period budget, with independent name-only payment methods. */
class BudgetManagementUi(
    private val root: View,
    private val onPreviousPeriod: () -> Unit,
    private val onNextPeriod: () -> Unit,
    private val onAddSource: (BudgetPeriod) -> Unit,
    private val onEditSource: (BudgetSource, BudgetPeriod) -> Unit,
    private val onEditBudget: (BudgetPeriod, Long) -> Unit,
) {
    private val context = root.context
    private val locale get() = context.resources.configuration.locales[0]
    private var currentSnapshot: BudgetSnapshot? = null

    init {
        root.findViewById<View>(R.id.budgetEditTotal).setOnClickListener {
            currentSnapshot?.let { onEditBudget(it.period, it.totalAllocatedMinor) }
        }
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
        listOf(R.id.budgetPreviousMonth, R.id.budgetNextMonth, R.id.budgetAddSource, R.id.budgetEditTotal).forEach {
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
            val row = LayoutInflater.from(context).inflate(R.layout.row_budget_source, rows, false)
            row.findViewById<TextView>(R.id.budgetSourceName).text = source.name
            row.findViewById<TextView>(R.id.budgetSourceAmount).visibility = View.GONE
            row.contentDescription = source.name
            row.setOnClickListener { onEditSource(source, period) }
            rows.addView(row)
        }
    }
}
