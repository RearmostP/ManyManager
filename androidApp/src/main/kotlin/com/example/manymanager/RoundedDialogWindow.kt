package com.example.manymanager

import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

/** Styles only these management dialogs while retaining the default window insets. */
fun AlertDialog.showWithRoundedSurface() {
    show()
    val dialogWindow = window ?: return
    val existingInsets = Rect()
    dialogWindow.decorView.background?.getPadding(existingInsets)
    val surface = ContextCompat.getDrawable(context, R.drawable.dialog_surface_rounded) ?: return
    val roundedBackground = InsetDrawable(
        surface,
        existingInsets.left,
        existingInsets.top,
        existingInsets.right,
        existingInsets.bottom,
    )
    dialogWindow.setBackgroundDrawable(roundedBackground)
    dialogWindow.decorView.clipToOutline = true
    findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
        ?.setTextColor(ContextCompat.getColor(context, R.color.text))
}
