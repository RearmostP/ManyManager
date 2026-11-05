package com.example.simple.manymanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import com.example.simple.manymanager.domain.Expense
import com.example.simple.manymanager.domain.mainCategoryId
import com.example.simple.manymanager.domain.BudgetCalculations
import java.text.NumberFormat

/** Both panels and their inline rows consume the containing Summary financial revision. */
class SummaryBreakdownUi(private val root: View, private val onRetry: () -> Unit,
    private val onExpense: (Expense) -> Unit = {},
    private val expandedGroups: MutableSet<String> = mutableSetOf()) {
    private val context = root.context
    private val expenseRows = AllExpensesUi(root, onExpense, onRetry)

    fun render(state: PersistentDataState) {
        val scroll = root.rootView.findViewById<ScrollView>(R.id.pageScroll)
        val top = scroll?.scrollY ?: 0
        renderPanel(state, R.id.categorySummaryRows, R.id.categorySummaryEmpty, true)
        renderPanel(state, R.id.sourceSummaryRows, R.id.sourceSummaryEmpty, false)
        scroll?.doOnPreDraw { scroll.scrollTo(0, top) }
    }

    private fun renderPanel(state: PersistentDataState, rowsId: Int, statusId: Int, category: Boolean) {
        val rows = root.findViewById<LinearLayout>(rowsId)
        rows.removeAllViews()
        val status = root.findViewById<TextView>(statusId)
        val message = when (state) {
            PersistentDataState.Loading -> R.string.expenses_loading
            is PersistentDataState.Failed -> R.string.expenses_load_error
            is PersistentDataState.Ready -> if (state.snapshot.expenseCount == 0) R.string.no_expenses else null
        }
        status.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(status::setText)
        status.isClickable = state is PersistentDataState.Failed
        status.isFocusable = status.isClickable
        status.setOnClickListener { if (state is PersistentDataState.Failed) onRetry() }
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        if (state !is PersistentDataState.Ready) return
        val snapshot = state.snapshot
        fun add(id: String?, name: String, spent: Long, count: Int) {
            val key = (if (category) "category:" else "source:") + id.orEmpty()
            val row = LayoutInflater.from(context).inflate(R.layout.row_summary_breakdown, rows, false)
            val locale = context.resources.configuration.locales[0]
            val amount = context.getString(R.string.ils_amount, BudgetAmounts.number(spent, locale))
            val countLabel = context.getString(R.string.breakdown_expense_count,
                NumberFormat.getIntegerInstance(locale).format(count))
            row.tag = id
            row.clipToOutline = true
            row.findViewById<TextView>(R.id.breakdownName).text = name
            row.findViewById<TextView>(R.id.breakdownAmount).text = amount
            row.findViewById<TextView>(R.id.breakdownCount).text = countLabel
            val header = row.findViewById<View>(R.id.summaryGroupHeader)
            header.contentDescription = "$name · $amount · $countLabel"
            header.isClickable = true
            header.isFocusable = true
            val pressed = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, pressed, true)
            header.foreground = context.getDrawable(pressed.resourceId)
            rows.addView(row)
            val list = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                tag = key
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            fun bindExpansion(animate: Boolean = false) {
                list.animate().cancel()
                list.alpha = 1f
                val expanded = key in expandedGroups
                row.findViewById<TextView>(R.id.breakdownChevron).text = if (expanded) "⌃" else "⌄"
                ViewCompat.setStateDescription(header, context.getString(if (expanded) R.string.group_expanded else R.string.group_collapsed))
                if (expanded) {
                    if (list.parent == null) (row as LinearLayout).addView(list)
                    val expenses = snapshot.expenses.filter {
                        if (category) snapshot.categories.mainCategoryId(it.categoryId) == id else it.sourceId == id
                    }
                    val children = snapshot.categories.filter { it.parentId == id }
                    if (category && id != null && children.isNotEmpty()) {
                        list.removeAllViews()
                        val sections = listOf(id to context.getString(R.string.category_general)) + children.map { it.id to it.name }
                        sections.forEach { (sectionId, sectionName) ->
                            val entries = expenses.filter { it.categoryId == sectionId }
                            if (entries.isNotEmpty()) {
                                val total = context.getString(R.string.ils_amount, BudgetAmounts.number(
                                    BudgetCalculations.sum(entries.map { it.amountMinor }), locale))
                                list.addView(TextView(context, null, 0, R.style.Text).apply {
                                    text = "$sectionName · $total"
                                    setPadding(20, 16, 20, 8)
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    isAccessibilityHeading = true
                                })
                                val section = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                                list.addView(section)
                                expenseRows.renderRows(section, snapshot, entries, true)
                            }
                        }
                    } else expenseRows.renderRows(list, snapshot, expenses, category)
                    if (animate && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                        list.alpha = .55f
                        list.animate().alpha(1f).setDuration(140L).start()
                    }
                } else (row as LinearLayout).removeView(list)
            }
            header.setOnClickListener {
                val scroll = root.rootView.findViewById<ScrollView>(R.id.pageScroll)
                val top = scroll?.scrollY ?: 0
                if (!expandedGroups.add(key)) expandedGroups.remove(key)
                bindExpansion(animate = true)
                scroll?.doOnPreDraw { scroll.scrollTo(0, top) }
            }
            bindExpansion()
        }
        if (category) {
            val names = snapshot.categories.associateBy { it.id }
            snapshot.categoryBreakdown.forEach {
                add(it.categoryId, names[it.categoryId]?.name ?: context.getString(R.string.uncategorized), it.spentMinor, it.expenseCount)
            }
        } else snapshot.sourceBreakdown.forEach { add(it.source.id, it.source.name, it.spentMinor, it.expenseCount) }
    }
}
