package com.example.manymanager

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Forms only; all writes and validation of allocation policy go through the shared model. */
object BudgetSourceDialogs {
    private fun monthLabel(context: Context, month: YearMonth): String =
        context.getString(R.string.budget_for_month,
            month.format(DateTimeFormatter.ofPattern("LLLL yyyy", context.resources.configuration.locales[0])))

    private fun money(context: Context, amount: Long): String =
        context.getString(R.string.ils_amount,
            BudgetAmounts.number(amount, context.resources.configuration.locales[0]))

    fun add(context: Context, state: BudgetViewModel, month: YearMonth, changed: () -> Unit): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_source, null)
        val name = view.findViewById<EditText>(R.id.addSourceName)
        val amount = view.findViewById<EditText>(R.id.addSourceAmount)
        view.findViewById<TextView>(R.id.addSourceMonth).text = monthLabel(context, month)
        amount.setText("0")
        val dialog = AlertDialog.Builder(context)
            .setView(view).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add_source, null).create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val parsed = BudgetAmounts.parse(amount.text.toString())
                if (name.text.toString().isBlank()) {
                    name.error = context.getString(R.string.source_name_required)
                    return@setOnClickListener
                }
                if (parsed == null || state.allocations.addSource(name.text.toString(), month, parsed) == null) {
                    amount.error = context.getString(R.string.budget_invalid_amount)
                    return@setOnClickListener
                }
                changed()
                dialog.dismiss()
            }
        }
        return dialog
    }

    fun edit(context: Context, state: BudgetViewModel, source: PaymentSource,
             month: YearMonth, changed: () -> Unit): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_source, null)
        val name = view.findViewById<EditText>(R.id.editSourceName)
        val amount = view.findViewById<EditText>(R.id.sourceAdjustment)
        val plus = view.findViewById<TextView>(R.id.sourcePlus)
        val minus = view.findViewById<TextView>(R.id.sourceMinus)
        val preview = view.findViewById<TextView>(R.id.sourcePreview)
        val current = state.allocations.amount(month, source.id)
        name.setText(source.name)
        view.findViewById<TextView>(R.id.editSourceMonth).text = monthLabel(context, month)
        view.findViewById<TextView>(R.id.currentSourceBudget).text =
            context.getString(R.string.current_budget, money(context, current))
        var add = true
        fun updatePreview() {
            val raw = amount.text.toString()
            val magnitude = if (raw.isBlank()) 0L else BudgetAmounts.parse(raw)
            val result = magnitude?.let {
                state.allocations.previewAdjustment(month, source.id, if (add) it else -it)
            }
            preview.text = if (result == null) context.getString(R.string.budget_invalid_amount)
                else context.getString(R.string.budget_after_change, money(context, result))
        }
        fun selectOperation(value: Boolean) {
            add = value
            plus.setBackgroundResource(if (add) R.drawable.chip_active else R.drawable.chip)
            minus.setBackgroundResource(if (add) R.drawable.chip else R.drawable.chip_active)
            plus.isSelected = add
            minus.isSelected = !add
            updatePreview()
        }
        plus.setOnClickListener { selectOperation(true) }
        minus.setOnClickListener { selectOperation(false) }
        amount.doAfterTextChanged { amount.error = null; updatePreview() }
        selectOperation(true)
        val dialog = AlertDialog.Builder(context)
            .setView(view).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.update_budget, null).create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (name.text.toString().isBlank()) {
                    name.error = context.getString(R.string.source_name_required)
                    return@setOnClickListener
                }
                val raw = amount.text.toString()
                val magnitude = if (raw.isBlank()) 0L else BudgetAmounts.parse(raw)
                if (magnitude == null) {
                    amount.error = context.getString(R.string.budget_invalid_amount)
                    return@setOnClickListener
                }
                val delta = if (add) magnitude else -magnitude
                if (!state.allocations.adjust(month, source.id, delta)) {
                    amount.error = context.getString(R.string.budget_invalid_amount)
                    return@setOnClickListener
                }
                state.allocations.renameSource(source.id, name.text.toString())
                changed()
                dialog.dismiss()
            }
        }
        return dialog
    }
}
