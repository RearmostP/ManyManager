package com.example.manymanager

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.example.manymanager.domain.*
import java.time.LocalDate

/** Captured period and draft survive recreation; uncertain submissions are never replayed. */
data class BudgetEditorState(
    val period: BudgetPeriod,
    val source: BudgetSource?,
    val name: String,
    val amount: String,
    val submitting: Boolean = false,
    val interrupted: Boolean = false,
    val error: DataError? = null,
) {
    fun toBundle() = Bundle().apply {
        putLong("start", period.startEpochDay)
        putLong("end", period.endExclusiveEpochDay)
        putString("source", source?.id)
        putString("owner", source?.calculatorId)
        putString("sourceName", source?.name)
        putString("name", name)
        putString("amount", amount)
        putBoolean("interrupted", interrupted || submitting)
    }
    companion object {
        fun fromBundle(b: Bundle) = BudgetEditorState(
            BudgetPeriod(LocalDate.ofEpochDay(b.getLong("start")), LocalDate.ofEpochDay(b.getLong("end"))),
            b.getString("source")?.let { BudgetSource(b.getString("owner").orEmpty(), it, b.getString("sourceName").orEmpty()) },
            b.getString("name").orEmpty(), b.getString("amount").orEmpty(),
            interrupted = b.getBoolean("interrupted"))
    }
}

class BudgetEditorDialog(
    context: Context,
    initial: BudgetEditorState,
    onChange: (String, String) -> Unit,
    onSave: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (24 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
    }
    private val heading = TextView(context, null, 0, R.style.Text).apply {
        setText(if (initial.source == null) R.string.overall_budget else R.string.source_spending_limit)
        textSize = 22f
    }
    private val period = TextView(context, null, 0, R.style.Text).apply {
        text = BudgetPeriodFormatter.description(context, initial.period)
    }
    private val name = EditText(context).apply {
        id = R.id.budgetEditorName
        setText(initial.name)
        setHint(R.string.source_name)
        visibility = if (initial.source == null) View.GONE else View.VISIBLE
        inputType = InputType.TYPE_CLASS_TEXT
    }
    private val amount = EditText(context).apply {
        id = R.id.budgetEditorAmount
        setText(initial.amount)
        setHint(R.string.optional_amount)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        textDirection = View.TEXT_DIRECTION_LTR
    }
    private val help = TextView(context, null, 0, R.style.Text).apply {
        setText(R.string.limit_independent_hint)
    }
    private var rendering = false
    private val scroll = ScrollView(context).apply { addView(content) }
    val dialog = AlertDialog.Builder(context).setView(scroll)
        .setPositiveButton(R.string.update_budget, null)
        .setNegativeButton(R.string.cancel, null)
        .setNeutralButton(R.string.remove_threshold, null).create()

    init {
        listOf(heading, period, name, amount, help).forEach(content::addView)
        name.doAfterTextChanged { if (!rendering) onChange(it.toString(), amount.text.toString()) }
        amount.doAfterTextChanged { if (!rendering) onChange(name.text.toString(), it.toString()) }
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onSave(false) }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { onSave(true) }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { onCancel() }
        }
    }
    fun show() = dialog.showWithBudgetSurface()
    fun render(state: BudgetEditorState) {
        rendering = true
        if (name.text.toString() != state.name) name.setText(state.name)
        if (amount.text.toString() != state.amount) amount.setText(state.amount)
        rendering = false
        val enabled = !state.submitting && !state.interrupted
        name.isEnabled = enabled
        amount.isEnabled = enabled
        dialog.setCancelable(!state.submitting)
        dialog.setCanceledOnTouchOutside(!state.submitting)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = enabled
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = enabled
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !state.submitting
        help.setText(when {
            state.interrupted -> R.string.source_save_interrupted
            state.error == DataError.BlankName -> R.string.source_name_required
            state.error == DataError.InvalidAmount || state.error == DataError.NegativeAllocatedBudget ||
                state.error == DataError.AmountOverflow -> R.string.budget_invalid_amount
            state.error != null -> R.string.source_save_error
            else -> R.string.limit_independent_hint
        })
    }
    fun dispose() { dialog.setOnCancelListener(null); dialog.dismiss() }
}
