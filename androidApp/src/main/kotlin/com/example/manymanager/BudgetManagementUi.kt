package com.example.manymanager

import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import java.time.format.DateTimeFormatter

/** Renders one calendar month's source allocations, with explicit apply callbacks. */
class BudgetManagementUi(
    private val root: View,
    private val state: BudgetViewModel,
    onManageSources: () -> Unit,
    private val onApplied: () -> Unit
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
        root.findViewById<View>(R.id.budgetManageSources).setOnClickListener { onManageSources() }
        render()
    }

    private fun money(amount: Long) =
        context.getString(R.string.ils_amount, BudgetAmounts.number(amount, locale))

    fun render() {
        val month = state.managementMonth
        root.findViewById<TextView>(R.id.budgetMonth).text =
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        updateTotal()
        root.findViewById<View>(R.id.budgetNoSources).visibility =
            if (state.allocations.sources.isEmpty()) View.VISIBLE else View.GONE
        val rows = root.findViewById<LinearLayout>(R.id.budgetSourceRows)
        rows.removeAllViews()
        state.allocations.sources.forEach { source ->
            val row = LayoutInflater.from(context).inflate(R.layout.row_budget_source, rows, false)
            row.findViewById<TextView>(R.id.budgetSourceName).text = source.name
            val amount = row.findViewById<EditText>(R.id.budgetSourceAmount)
            val apply = row.findViewById<TextView>(R.id.budgetApply)
            val key = BudgetViewModel.DraftKey(month, source.id)
            // Preserve pasted/physical input for validation, while requesting a decimal numeric IME.
            amount.keyListener = android.text.method.TextKeyListener.getInstance()
            amount.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
            amount.contentDescription = context.getString(R.string.budget_amount_for_source, source.name)
            amount.setText(state.drafts[key] ?: BudgetAmounts.number(
                state.allocations.amount(month, source.id), locale, grouping = false
            ))
            amount.doAfterTextChanged {
                state.drafts[key] = it.toString()
                amount.error = null
            }
            fun applyAmount() {
                val parsed = BudgetAmounts.parse(amount.text.toString())
                if (parsed == null || !state.allocations.apply(month, source.id, parsed)) {
                    amount.error = context.getString(R.string.budget_invalid_amount)
                    amount.requestFocus()
                    return
                }
                amount.error = null
                updateTotal()
                onApplied()
                apply.announceForAccessibility(context.getString(R.string.budget_amount_applied, source.name))
            }
            apply.setOnClickListener { applyAmount() }
            amount.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    applyAmount()
                    true
                } else false
            }
            rows.addView(row)
        }
    }

    private fun updateTotal() {
        root.findViewById<TextView>(R.id.budgetAllocatedTotal).text =
            money(state.allocations.total(state.managementMonth))
    }
}
