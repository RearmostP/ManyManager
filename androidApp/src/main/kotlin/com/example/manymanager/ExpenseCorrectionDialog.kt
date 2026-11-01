package com.example.manymanager

import android.app.Activity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.example.manymanager.domain.DataResult

/** A window only: the controller owns input and the pending transaction. */
class ExpenseCorrectionDialog(
    private val activity: Activity,
    onAmount: (String) -> Unit,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit,
) {
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private var rendering = false
    private var state: ExpenseCorrectionState? = null
    private val amount = EditText(activity).apply {
        id = R.id.expenseCorrectionAmount
        inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        setSingleLine()
        minHeight = dp(48)
        hint = activity.getString(R.string.expense_amount)
        contentDescription = hint
        setTextColor(activity.getColor(R.color.text))
        setHintTextColor(activity.getColor(R.color.muted))
        doAfterTextChanged { if (!rendering) onAmount(it.toString()) }
    }
    private val status = TextView(activity).apply {
        id = R.id.expenseCorrectionStatus
        setTextColor(activity.getColor(R.color.text))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    val dialog: AlertDialog = AlertDialog.Builder(activity)
        .setTitle(R.string.correct_expense_amount)
        .setView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            addView(amount); addView(status)
        })
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.confirm_amount_correction, null)
        .create()

    fun show() {
        dialog.setOnCancelListener { if (state?.submitting != true) onCancel() }
        dialog.showWithRoundedSurface()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { onCancel() }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onConfirm() }
    }

    fun render(value: ExpenseCorrectionState) {
        state = value
        rendering = true
        if (amount.text.toString() != value.amountText) amount.setText(value.amountText)
        rendering = false
        val locked = value.submitting || value.interrupted
        amount.isEnabled = !locked
        dialog.setCancelable(!value.submitting)
        dialog.setCanceledOnTouchOutside(!value.submitting)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            isEnabled = !value.submitting
            setText(if (value.interrupted) R.string.review_saved_budget else R.string.cancel)
        }
        val parsed = BudgetAmounts.positive(value.amountText)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isEnabled = !locked && parsed is DataResult.Success
            setText(if (value.submitting) R.string.expense_saving else R.string.confirm_amount_correction)
        }
        val message = when {
            value.interrupted -> R.string.expense_save_interrupted
            value.error != null -> expenseErrorMessage(value.error)
            parsed is DataResult.Failure -> expenseErrorMessage(parsed.error)
            else -> null
        }
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(status::setText)
    }

    fun dispose() { dialog.setOnCancelListener(null); dialog.dismiss() }
}
