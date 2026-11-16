package com.example.manymanager

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog

/** Shared rounded budget surface and action styling. */
fun AlertDialog.showWithBudgetSurface() {
    show()
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    window?.apply {
        setBackgroundDrawableResource(R.drawable.budget_dialog_surface)
        decorView.clipToOutline = true
        addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setDimAmount(.38f)
        setGravity(Gravity.CENTER)
        val bounds = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
        setLayout((minOf(bounds.width(), dp(440)) - dp(32)).coerceAtLeast(1), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    styleBudgetDialogButtons()
}

/** Also used by setup/category dialogs while preserving their own window sizing. */
fun AlertDialog.styleBudgetDialogButtons() {
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    for (which in listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL)) {
        getButton(which).apply {
            isAllCaps = false
            setSingleLine(false)
            minHeight = dp(48)
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (which == AlertDialog.BUTTON_POSITIVE) {
                setBackgroundResource(R.drawable.action_primary_background)
                backgroundTintList = null
                setTextColor(context.getColorStateList(R.color.action_primary_text))
            } else {
                setBackgroundResource(R.drawable.action_secondary_background)
                backgroundTintList = null
                setTextColor(context.getColorStateList(R.color.action_secondary_text))
            }
        }
    }
}
