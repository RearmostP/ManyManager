package com.example.manymanager

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.manymanager.domain.Category

/** Presentation adapted from original: main retains flat, optional categories. */
class CategoryPickerDialog(
    private val context: Context,
    categories: List<Category>,
    selectedId: String?,
    private val onSelect: (String?) -> Unit,
) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private val rows = LinearLayout(context).apply {
        id = R.id.categoryPickerRows
        orientation = LinearLayout.VERTICAL
        layoutDirection = resources.configuration.layoutDirection
        setPadding(dp(20), dp(12), dp(20), dp(12))
    }
    private val scroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = (resources.displayMetrics.heightPixels * .58f).toInt()
            val available = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) cap
                else minOf(cap, MeasureSpec.getSize(heightMeasureSpec))
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST))
        }
    }.apply { addView(rows) }
    val dialog: AlertDialog = AlertDialog.Builder(context)
        .setTitle(R.string.choose_category)
        .setView(scroll)
        .setNegativeButton(R.string.cancel, null)
        .create()

    init {
        val options = listOf(null to context.getString(R.string.uncategorized)) +
            categories.map { it.id to it.name }
        options.forEach { (id, name) ->
            rows.addView(TextView(context, null, 0, R.style.Text).apply {
                text = name
                tag = id
                textSize = 14f
                minHeight = dp(48)
                setPadding(dp(16), dp(16), dp(16), dp(16))
                isSelected = id == selectedId
                setTextColor(context.getColor(if (isSelected) R.color.blue else R.color.text))
                setBackgroundResource(if (isSelected) R.drawable.chip_active else R.drawable.chip)
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (isSelected) R.drawable.ic_check else 0, 0)
                compoundDrawablePadding = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { dialog.dismiss(); onSelect(id) }
            })
        }
    }
    fun show() {
        dialog.showWithRoundedSurface()
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            isAllCaps = false
            setSingleLine(false)
            minHeight = dp(48)
            setTextColor(context.getColor(R.color.muted))
        }
    }
    fun dispose() = dialog.dismiss()
}
