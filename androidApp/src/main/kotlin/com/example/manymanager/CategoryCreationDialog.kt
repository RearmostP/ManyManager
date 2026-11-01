package com.example.manymanager

import android.content.Context
import android.content.DialogInterface
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.example.manymanager.domain.DataError
import com.example.manymanager.domain.DataResult

/** Window lifetime only. The ViewModel owns the form and operation across recreation. */
class CategoryCreationDialog(
    context: Context,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    private val input = EditText(context).apply {
        id = R.id.categoryNameInput
        hint = context.getString(R.string.category_name)
        setSingleLine(true)
        val density = resources.displayMetrics.density
        setPadding((24 * density).toInt(), (14 * density).toInt(),
            (24 * density).toInt(), (14 * density).toInt())
        setTextColor(ContextCompat.getColor(context, R.color.text))
        setHintTextColor(ContextCompat.getColor(context, R.color.muted))
    }
    val dialog: AlertDialog = AlertDialog.Builder(context)
        .setTitle(R.string.add_category_action).setView(input)
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.add_category, null).create()

    init {
        input.doAfterTextChanged { onNameChanged(it.toString()) }
        dialog.setOnCancelListener { onCancel() }
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { onSubmit() }
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { onCancel() }
        }
    }

    fun show() { dialog.showWithRoundedSurface() }

    fun render(state: CategoryCreationState) {
        if (input.text.toString() != state.name) {
            input.setText(state.name)
            input.setSelection(input.length())
        }
        input.isEnabled = !state.submitting
        dialog.setCancelable(!state.submitting)
        dialog.setCanceledOnTouchOutside(!state.submitting)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).apply {
            isEnabled = !state.submitting && state.result !is DataResult.Success
            setText(if (state.submitting) R.string.category_saving else R.string.add_category)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = !state.submitting
        val error = (state.result as? DataResult.Failure)?.error
        input.error = when {
            error == DataError.BlankName -> input.context.getString(R.string.category_name_required)
            error != null -> input.context.getString(R.string.category_save_error)
            state.interrupted -> input.context.getString(R.string.category_save_interrupted)
            else -> null
        }
    }

    /** Detach a window for configuration/destruction without cancelling or retrying a write. */
    fun dispose() {
        dialog.setOnCancelListener(null)
        dialog.dismiss()
    }
}
