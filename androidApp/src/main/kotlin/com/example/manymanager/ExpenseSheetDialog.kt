package com.example.manymanager

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.view.doOnPreDraw

/** Slides only the expense sheet away, leaving the Activity beneath it in place. */
class ExpenseSheetDialog(context: Context) : Dialog(context) {
    var sheetView: View? = null
    var isDismissing = false
        private set

    override fun show() {
        val alreadyShowing = isShowing
        super.show()
        val sheet = sheetView ?: return
        if (alreadyShowing || !ValueAnimator.areAnimatorsEnabled()) return
        // Start below the viewport, then animate only after layout and insets are ready.
        sheet.translationY = context.resources.displayMetrics.heightPixels.toFloat()
        sheet.doOnPreDraw {
            if (isShowing && !isDismissing) {
                sheet.animate()
                    .translationY(0f)
                    .setDuration(320L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    override fun dismiss() {
        if (isDismissing) return
        val sheet = sheetView
        if (!isShowing || sheet == null || !sheet.isAttachedToWindow ||
            !ValueAnimator.areAnimatorsEnabled()
        ) {
            dismissImmediately()
            return
        }
        isDismissing = true
        val parentHeight = (sheet.parent as? View)?.height ?: sheet.height
        // Include the bottom inset, so no part of the sheet remains in the window.
        val offscreen = (parentHeight - sheet.top).toFloat()
        sheet.animate().cancel()
        sheet.animate()
            .translationY(offscreen)
            .setDuration(220L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { dismissImmediately() }
            .start()
    }

    /** Used when the Activity replaces its UI or is destroyed; never leave a window behind. */
    fun dismissImmediately() {
        sheetView?.animate()?.cancel()
        super.dismiss()
        sheetView = null
        isDismissing = false
    }
}
