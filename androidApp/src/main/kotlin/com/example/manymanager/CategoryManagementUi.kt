package com.example.manymanager

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Displays prepared category state and submits actions; owns no mutable data list. */
class CategoryManagementUi(
    private val root: View,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    private val onRetry: () -> Unit,
) {
    init {
        root.findViewById<View>(R.id.categoryBack).setOnClickListener { onBack() }
        root.findViewById<View>(R.id.categoryAdd).setOnClickListener { onAdd() }
    }

    fun render(state: CategoryListState) {
        val status = root.findViewById<TextView>(R.id.categoriesEmpty)
        val message = state.statusMessage()
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let { status.setText(it) }
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        status.setOnClickListener { onRetry() }
        status.isClickable = state is CategoryListState.Failed
        status.isFocusable = state is CategoryListState.Failed
        val rows = root.findViewById<LinearLayout>(R.id.categoryRows)
        rows.removeAllViews()
        if (state !is CategoryListState.Ready) return
        state.categories.forEach { category ->
            rows.addView(TextView(root.context, null, 0, R.style.Text).apply {
                tag = category.id
                text = category.name
                textSize = 16f
                val inset = (20 * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
                setBackgroundResource(R.drawable.card)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
            })
        }
    }
}
