package com.example.manymanager

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.example.manymanager.domain.DataError
import com.example.manymanager.domain.DataResult

class SourceCreationDialog(
    context: Context,
    onNameChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    private val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_source, null)
    private val nameInput = view.findViewById<EditText>(R.id.addSourceName)
    private val amountInput = view.findViewById<EditText>(R.id.addSourceAmount)
    private val monthLabel = view.findViewById<TextView>(R.id.addSourceMonth)

    val dialog: AlertDialog = AlertDialog.Builder(context)
        .setView(view)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.add_source, null)
        .create()

    init {
        nameInput.doAfterTextChanged { onNameChanged(it.toString()) }
        amountInput.doAfterTextChanged { onAmountChanged(it.toString()) }
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onSubmit() }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { onCancel() }
        }
    }

    fun show() { dialog.showWithRoundedSurface() }

    fun render(state: SourceCreationState) {
        monthLabel.text = context.getString(
            R.string.budget_for_month, BudgetPeriodFormatter.format(context, state.period)
        )
        if (nameInput.text.toString() != state.name) {
            nameInput.setText(state.name)
            nameInput.setSelection(nameInput.length())
        }
        if (amountInput.text.toString() != state.initialAmountText) {
            amountInput.setText(state.initialAmountText)
            amountInput.setSelection(amountInput.length())
        }
        nameInput.isEnabled = !state.submitting
        amountInput.isEnabled = !state.submitting
        dialog.setCancelable(!state.submitting)
        dialog.setCanceledOnTouchOutside(!state.submitting)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).apply {
            isEnabled = !state.submitting && state.result !is DataResult.Success
            setText(if (state.submitting) R.string.source_saving else R.string.add_source)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = !state.submitting

        val error = (state.result as? DataResult.Failure)?.error
        nameInput.error = when (error) {
            DataError.BlankName -> context.getString(R.string.source_name_required)
            else -> null
        }
        amountInput.error = when {
            error == DataError.NegativeAllocatedBudget || error == DataError.AmountOverflow ->
                context.getString(R.string.budget_invalid_amount)
            error != null && error != DataError.BlankName ->
                context.getString(R.string.source_save_error)
            state.interrupted ->
                context.getString(R.string.source_save_interrupted)
            else -> null
        }
    }

    fun dispose() {
        dialog.setOnCancelListener(null)
        dialog.dismiss()
    }

    private val context get() = view.context
}

class SourceEditingDialog(
    context: Context,
    onNameChanged: (String) -> Unit,
    onAdjustmentChanged: (String, Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    private val view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_source, null)
    private val nameInput = view.findViewById<EditText>(R.id.editSourceName)
    private val amountInput = view.findViewById<EditText>(R.id.sourceAdjustment)
    private val monthLabel = view.findViewById<TextView>(R.id.editSourceMonth)
    private val currentLabel = view.findViewById<TextView>(R.id.currentSourceBudget)
    private val plusBtn = view.findViewById<TextView>(R.id.sourcePlus)
    private val minusBtn = view.findViewById<TextView>(R.id.sourceMinus)
    private val previewText = view.findViewById<TextView>(R.id.sourcePreview)

    private var isAdd = true

    val dialog: AlertDialog = AlertDialog.Builder(context)
        .setView(view)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.update_budget, null)
        .create()

    init {
        nameInput.doAfterTextChanged { onNameChanged(it.toString()) }
        amountInput.doAfterTextChanged { onAdjustmentChanged(it.toString(), isAdd) }
        plusBtn.setOnClickListener {
            isAdd = true
            onAdjustmentChanged(amountInput.text.toString(), true)
        }
        minusBtn.setOnClickListener {
            isAdd = false
            onAdjustmentChanged(amountInput.text.toString(), false)
        }
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onSubmit() }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { onCancel() }
        }
    }

    fun show() { dialog.showWithRoundedSurface() }

    private fun money(amount: Long): String =
        context.getString(R.string.ils_amount, BudgetAmounts.number(amount, context.resources.configuration.locales[0]))

    fun render(state: SourceEditingState) {
        isAdd = state.isAdd
        monthLabel.text = context.getString(
            R.string.budget_for_month, BudgetPeriodFormatter.format(context, state.period)
        )
        currentLabel.text = context.getString(
            R.string.current_budget, money((state.previewResult as? DataResult.Success)?.value?.currentAllocatedMinor ?: state.currentAllocationMinor)
        )
        if (nameInput.text.toString() != state.name) {
            nameInput.setText(state.name)
            nameInput.setSelection(nameInput.length())
        }
        if (amountInput.text.toString() != state.adjustmentText) {
            amountInput.setText(state.adjustmentText)
            amountInput.setSelection(amountInput.length())
        }
        plusBtn.setBackgroundResource(if (isAdd) R.drawable.chip_active else R.drawable.chip)
        minusBtn.setBackgroundResource(if (isAdd) R.drawable.chip else R.drawable.chip_active)
        plusBtn.isSelected = isAdd
        minusBtn.isSelected = !isAdd
        plusBtn.setTextColor(context.getColor(if (isAdd) R.color.blue else R.color.text))
        minusBtn.setTextColor(context.getColor(if (isAdd) R.color.text else R.color.blue))

        nameInput.isEnabled = !state.submitting
        amountInput.isEnabled = !state.submitting
        plusBtn.isEnabled = !state.submitting
        minusBtn.isEnabled = !state.submitting
        dialog.setCancelable(!state.submitting)
        dialog.setCanceledOnTouchOutside(!state.submitting)

        val preview = state.previewResult
        val isValidPreview = preview is DataResult.Success && preview.value.resultingAllocatedMinor >= 0
        if (preview is DataResult.Success) {
            previewText.text = context.getString(
                R.string.budget_after_change, money(preview.value.resultingAllocatedMinor)
            )
        } else {
            previewText.text = context.getString(if (preview == null) R.string.budget_loading else R.string.budget_invalid_amount)
        }

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).apply {
            isEnabled = !state.submitting && isValidPreview && state.name.isNotBlank() && state.submitResult !is DataResult.Success
            setText(if (state.submitting) R.string.source_saving else R.string.update_budget)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = !state.submitting

        val error = (state.submitResult as? DataResult.Failure)?.error
        nameInput.error = when (error) {
            DataError.BlankName -> context.getString(R.string.source_name_required)
            else -> null
        }
        amountInput.error = when {
            error == DataError.NegativeAllocatedBudget || error == DataError.AmountOverflow ->
                context.getString(R.string.budget_invalid_amount)
            error != null && error != DataError.BlankName ->
                context.getString(R.string.source_save_error)
            state.interrupted ->
                context.getString(R.string.source_save_interrupted)
            else -> null
        }
    }

    fun dispose() {
        dialog.setOnCancelListener(null)
        dialog.dismiss()
    }

    private val context get() = view.context
}
