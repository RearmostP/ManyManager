package com.example.manymanager

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import com.example.manymanager.domain.Category


/** Displays prepared category state and submits actions; owns no mutable data list. */
class CategoryManagementUi(
    private val root: View,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    private val onRetry: () -> Unit,
    private val onEdit: (Category) -> Unit,
    private val onAddChild: (Category) -> Unit,
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
        fun categoryRow(category: Category) = LinearLayout(root.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val inset = (12 * resources.displayMetrics.density).toInt()
            setPadding(inset, inset / 2, inset, inset / 2)
            addView(TextView(root.context, null, 0, R.style.Text).apply {
                tag = category.id
                text = category.name
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Button(root.context, null, 0, R.style.ActionButton_Secondary).apply {
                setText(R.string.rename_category)
                contentDescription = "${text}: ${category.name}"
                setOnClickListener { onEdit(category) }
            })
        }
        state.categories.filter { it.parentId == null }.forEach { parent ->
            val card = LinearLayout(root.context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.card)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (16 * resources.displayMetrics.density).toInt() }
            }
            card.addView(categoryRow(parent))
            state.categories.filter { it.parentId == parent.id }.forEach { child ->
                card.addView(categoryRow(child).apply {
                    setPaddingRelative((32 * resources.displayMetrics.density).toInt(), paddingTop,
                        paddingEnd, paddingBottom)
                })
            }
            card.addView(Button(root.context, null, 0, R.style.ActionButton_Secondary).apply {
                setText(R.string.add_subcategory)
                contentDescription = "${text}: ${parent.name}"
                setOnClickListener { onAddChild(parent) }
            })
            rows.addView(card)
        }
    }
}