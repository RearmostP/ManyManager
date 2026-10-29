package com.example.manymanager

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.view.View
import android.view.animation.AccelerateInterpolator

/** Returns the menu to the same edge from which it entered. */
class SideMenuDialog(context: Context) : Dialog(context) {
    var panelView: View? = null
    var opensFromRight = false
    private var isDismissing = false

    override fun dismiss() {
        if (isDismissing) return
        val panel = panelView
        if (!isShowing || panel == null || !panel.isAttachedToWindow ||
            !ValueAnimator.areAnimatorsEnabled()
        ) {
            dismissImmediately()
            return
        }
        isDismissing = true
        panel.animate().cancel()
        panel.animate()
            .translationX(if (opensFromRight) panel.width.toFloat() else -panel.width.toFloat())
            .setDuration(320L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { dismissImmediately() }
            .start()
    }

    fun dismissImmediately() {
        panelView?.animate()?.cancel()
        super.dismiss()
        panelView = null
        isDismissing = false
    }
}
