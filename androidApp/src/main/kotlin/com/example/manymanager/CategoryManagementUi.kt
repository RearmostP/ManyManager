package com.example.manymanager

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Dedicated category list; category mutations live in the Activity ViewModel. */
class CategoryManagementUi(
    private val root: View,
    private val state: BudgetViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    init {
        root.findViewById<View>(R.id.categoryBack).setOnClickListener { onBack() }
        root.findViewById<View>(R.id.categoryAdd).setOnClickListener { onAdd() }
        render()
    }

    fun render() {
        root.findViewById<View>(R.id.categoriesEmpty).visibility =
            if (state.categories.isEmpty()) View.VISIBLE else View.GONE
        val rows = root.findViewById<LinearLayout>(R.id.categoryRows)
        rows.removeAllViews()
        state.categories.forEach { name ->
            rows.addView(TextView(root.context, null, 0, R.style.Text).apply {
                text = name
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
