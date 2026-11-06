package com.example.manymanager

import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog

/** original's budget dialog styling, wired to main's unchanged source allocation forms. */
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
    for (which in listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE)) {
        getButton(which).apply {
            isAllCaps = false
            setSingleLine(false)
            minHeight = dp(48)
            textSize = 14f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            if (which == AlertDialog.BUTTON_POSITIVE) {
                setBackgroundResource(R.drawable.budget_dialog_primary)
                backgroundTintList = null
                setTextColor(context.getColor(R.color.white))
            } else setTextColor(context.getColor(R.color.muted))
        }
    }
}
