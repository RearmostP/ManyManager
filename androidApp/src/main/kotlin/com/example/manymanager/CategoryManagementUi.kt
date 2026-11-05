package com.example.simple.manymanager

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Displays prepared category state and submits actions; owns no mutable data list. */
class CategoryManagementUi(
    private val root: View,
    private val onBack: () -> Unit,
    private val onAdd: () -> Unit,
    private val onRetry: () -> Unit,
) {
    var parentId: String? = null
    private var state: CategoryListState = CategoryListState.Loading
    fun back(): Boolean {
        if (parentId == null) return false
        parentId = null; render(state); return true
    }
    init {
        root.findViewById<View>(R.id.categoryBack).setOnClickListener { if (!back()) onBack() }
        root.findViewById<View>(R.id.categoryAdd).setOnClickListener { onAdd() }
    }

    fun render(state: CategoryListState) {
        this.state = state
        val ready = (state as? CategoryListState.Ready)?.categories.orEmpty()
        val parent = ready.firstOrNull { it.id == parentId && it.parentId == null }
        root.findViewById<TextView>(R.id.categoryPageTitle).text = parent?.name ?: root.context.getString(R.string.expense_categories)
        root.findViewById<TextView>(R.id.categoryAdd).setText(if (parentId == null) R.string.add_category_action else R.string.add_subcategory)
        val status = root.findViewById<TextView>(R.id.categoriesEmpty)
        val message = if (state is CategoryListState.Ready && parentId != null && ready.none { it.parentId == parentId })
            R.string.no_subcategories else state.statusMessage()
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let { status.setText(it) }
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        status.setOnClickListener { onRetry() }
        status.isClickable = state is CategoryListState.Failed
        status.isFocusable = state is CategoryListState.Failed
        val rows = root.findViewById<LinearLayout>(R.id.categoryRows)
        rows.removeAllViews()
        if (state !is CategoryListState.Ready) return
        state.categories.filter { it.parentId == parentId }.forEach { category ->
            rows.addView(TextView(root.context, null, 0, R.style.Text).apply {
                tag = category.id
                if (parentId == null) {
                    isClickable = true; isFocusable = true
                    setOnClickListener { parentId = category.id; render(state) }
                }
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
